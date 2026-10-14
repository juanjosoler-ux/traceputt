package com.puttvision.feature.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaMetadataRetriever
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.puttvision.audio.AudioImpactDetector
import com.puttvision.audio.AudioLevelMonitor
import com.puttvision.camera.CircularFrameBuffer
import com.puttvision.camera.HighSpeedCameraHelper
import com.puttvision.camera.HighSpeedCameraSession
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.core.domain.model.PuttRecord
import com.puttvision.vision.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import javax.inject.Inject

sealed class RecordingCommand {
    object StartClip : RecordingCommand()
    object StopClip  : RecordingCommand()
}

@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val ctx    : Context,
    private val sessionRepo        : SessionRepository,
    private val puttRepo           : PuttRepository,
    private val addressDetector    : AddressDetector,
    private val analysisEngine     : PuttAnalysisEngine,
    private val colorMarkerDetector: ColorMarkerDetector,
    private val ballLaunchDetector : com.puttvision.vision.BallLaunchDetector,
    private val audioDetector      : AudioImpactDetector,
    private val levelMonitor       : AudioLevelMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val _recordingCommand = MutableSharedFlow<RecordingCommand>(
        extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val recordingCommand: SharedFlow<RecordingCommand> = _recordingCommand.asSharedFlow()

    private val poseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val frameBuffer   = CircularFrameBuffer(capacity = 90)
    /** Optional Camera2 high-speed session — non-null when 120fps mode is active */
    private var hsSession     : HighSpeedCameraSession? = null

    private var sessionId    : Long    = 0L
    private var lastVideoPath: String? = null
    private var analyzeJob   : Job?    = null
    private var imageWidth   : Int     = 1
    private var imageHeight  : Int     = 1

    // ─────────────────────────────────────────────────────────────────────────

    fun init(id: Long) {
        if (sessionId == id) return
        sessionId = id

        val hsInfo = HighSpeedCameraHelper.queryHighSpeedSupport(ctx)
        _uiState.update { it.copy(supportsHighSpeed = hsInfo.supports120fps) }

        viewModelScope.launch {
            val session = sessionRepo.getById(id)
            _uiState.update { it.copy(session = session) }
            session?.let { s -> analysisEngine.onSessionStart(s.perspectiveType, imageWidth) }
        }
        viewModelScope.launch {
            puttRepo.observeCountBySession(id).collect { n ->
                _uiState.update { it.copy(puttCount = n) }
            }
        }
        transitionTo(LiveSessionState.SEARCHING_ADDRESS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 120fps toggle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Toggle high-speed Camera2 mode. When enabled, [HighSpeedCameraSession]
     * takes over video recording from CameraX VideoCapture.
     * CameraX ImageAnalysis (ML Kit) continues as normal at standard FPS.
     */
    fun toggleHighSpeed() {
        val current = _uiState.value.highSpeedEnabled
        if (current) {
            // Disable — close Camera2 session, CameraX resumes control
            hsSession?.close()
            hsSession = null
            _uiState.update { it.copy(highSpeedEnabled = false) }
        } else {
            // Enable — open Camera2 high-speed session
            if (!_uiState.value.supportsHighSpeed) return
            hsSession = HighSpeedCameraSession(ctx)
            hsSession!!.open(
                targetFps = 120,
                onReady = {
                    _uiState.update { it.copy(highSpeedEnabled = true) }
                },
                onError = { msg ->
                    android.util.Log.w("LiveVM", "High-speed open failed: $msg")
                    hsSession = null
                }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera frame
    // ─────────────────────────────────────────────────────────────────────────

    fun onCameraFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }

        if (imageWidth == 1) {
            imageWidth  = imageProxy.width
            imageHeight = imageProxy.height
            analysisEngine.onSessionStart(
                _uiState.value.session?.perspectiveType
                    ?: com.puttvision.core.domain.model.PerspectiveType.BEHIND,
                imageWidth
            )
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val state = _uiState.value.sessionState
        val ts    = System.currentTimeMillis()

        poseDetector.process(input)
            .addOnSuccessListener { pose ->
                _uiState.update { it.copy(skeletonLandmarks = pose.toNormLandmarks(imageWidth, imageHeight)) }

                val bitmap: Bitmap? = when (state) {
                    LiveSessionState.BUFFERING,
                    LiveSessionState.IMPACT_DETECTED -> imageProxy.yuvToBitmap()
                    else -> null
                }
                bitmap?.let { frameBuffer.push(it, ts) }

                when (state) {
                    LiveSessionState.SEARCHING_ADDRESS -> {
                        val result   = addressDetector.analyze(pose, ts)
                        val progress = if (result is AddressResult.Stabilising) result.progress else 0f
                        _uiState.update { it.copy(addressResult = result, addressProgress = progress) }
                        if (result == AddressResult.Confirmed) {
                            addressDetector.reset()
                            analysisEngine.onAddressConfirmed()
                            transitionTo(LiveSessionState.READY)
                        }
                    }
                    LiveSessionState.READY -> {
                        val result = addressDetector.analyze(pose, ts)
                        if (result is AddressResult.Searching || result is AddressResult.WristsNotDown) {
                            onStrokeStart(ts)
                        }
                    }
                    LiveSessionState.BUFFERING,
                    LiveSessionState.IMPACT_DETECTED -> analysisEngine.onFrame(pose, bitmap, ts)
                    else -> {}
                }
            }
            .addOnFailureListener { _uiState.update { it.copy(skeletonLandmarks = emptyList()) } }
            .addOnCompleteListener { imageProxy.close() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio
    // ─────────────────────────────────────────────────────────────────────────

    fun startAudioDetection() {
        viewModelScope.launch {
            audioDetector.start(this)
            levelMonitor.start(this)
            _uiState.update { it.copy(isAudioArmed = true) }
        }
        viewModelScope.launch {
            audioDetector.impactEvents.collect { ts ->
                if (_uiState.value.sessionState == LiveSessionState.BUFFERING) onImpactDetected(ts)
            }
        }
        viewModelScope.launch {
            levelMonitor.level.collect { lvl -> _uiState.update { it.copy(audioLevel = lvl) } }
        }
    }

    fun stopAudioDetection() {
        viewModelScope.launch {
            audioDetector.stop()
            levelMonitor.stop()
            _uiState.update { it.copy(isAudioArmed = false, audioLevel = 0f) }
        }
    }

    fun onVideoSaved(path: String) { lastVideoPath = path }

    // ─────────────────────────────────────────────────────────────────────────
    // State machine
    // ─────────────────────────────────────────────────────────────────────────

    private fun transitionTo(state: LiveSessionState) =
        _uiState.update { it.copy(sessionState = state) }

    private fun onStrokeStart(ts: Long) {
        analysisEngine.onStrokeStart(ts)
        if (_uiState.value.highSpeedEnabled) {
            hsSession?.startRecording()
        } else {
            _recordingCommand.tryEmit(RecordingCommand.StartClip)
        }
        transitionTo(LiveSessionState.BUFFERING)
    }

    private fun onImpactDetected(ts: Long) {
        audioDetector.disarm()
        analysisEngine.onImpact(ts)
        if (_uiState.value.highSpeedEnabled) {
            lastVideoPath = hsSession?.stopRecording()
        } else {
            _recordingCommand.tryEmit(RecordingCommand.StopClip)
        }
        transitionTo(LiveSessionState.IMPACT_DETECTED)
        analyzeJob = viewModelScope.launch {
            delay(650)
            analysisEngine.onFollowEnd(System.currentTimeMillis())
            runAnalysis()
        }
    }

    private suspend fun runAnalysis() {
        transitionTo(LiveSessionState.ANALYZING)
        val metrics  = analysisEngine.analyze(puttId = 0L)
        val path     = lastVideoPath.also { lastVideoPath = null }
        val puttId   = puttRepo.insertRecord(
            PuttRecord(sessionId = sessionId, videoPath = path, detectionConfidence = metrics.overallConf)
        )
        puttRepo.upsertMetrics(metrics.copy(puttId = puttId))
        if (path != null) {
            withContext(Dispatchers.IO) {
                generateThumbnail(path)?.let { t -> puttRepo.updatePaths(puttId, path, t) }
            }
        }
        frameBuffer.release()
        _uiState.update { it.copy(lastMetrics = metrics.copy(puttId = puttId)) }
        transitionTo(LiveSessionState.SAVED)
        delay(2_500)
        addressDetector.reset()
        analysisEngine.onAddressConfirmed()
        audioDetector.rearm()
        transitionTo(LiveSessionState.SEARCHING_ADDRESS)
    }

    fun manualImpact() {
        if (_uiState.value.sessionState == LiveSessionState.BUFFERING) {
            onImpactDetected(System.currentTimeMillis())
        }
    }

    private fun generateThumbnail(videoPath: String): String? = runCatching {
        val r = MediaMetadataRetriever()
        r.setDataSource(videoPath)
        val bmp = r.getFrameAtTime(500_000L) ?: return@runCatching null
        r.release()
        val p = videoPath.replace(".mp4", "_thumb.jpg")
        FileOutputStream(p).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bmp.recycle()
        p
    }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        poseDetector.close()
        colorMarkerDetector.release()
        ballLaunchDetector.release()
        frameBuffer.release()
        hsSession?.close()
        analyzeJob?.cancel()
        viewModelScope.launch { audioDetector.stop(); levelMonitor.stop() }
    }
}

private fun ImageProxy.yuvToBitmap(): Bitmap? = runCatching {
    val yBuf = planes[0].buffer; val uBuf = planes[1].buffer; val vBuf = planes[2].buffer
    val ySize = yBuf.remaining(); val uSize = uBuf.remaining(); val vSize = vBuf.remaining()
    val nv21  = ByteArray(ySize + uSize + vSize)
    yBuf.get(nv21, 0, ySize); vBuf.get(nv21, ySize, vSize); uBuf.get(nv21, ySize + vSize, uSize)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 60, out)
    BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}.getOrNull()
