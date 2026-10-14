package com.puttvision.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "HighSpeedCameraSession"

/**
 * Camera2-based constrained high-speed capture session.
 *
 * This is a *separate* capture path from CameraX. CameraX does not expose the
 * constrained high-speed API (`createConstrainedHighSpeedCaptureSession`), so
 * we use Camera2 directly for 120fps capture.
 *
 * Usage flow:
 *   1. Call [open] — opens the Camera2 device and creates the high-speed session.
 *   2. Call [startRecording] — starts recording to an MP4 file.
 *   3. Call [stopRecording] — stops recording, returns the file path.
 *   4. Call [close] — releases all resources.
 *
 * Integration with PuttVision:
 *   When [HighSpeedCameraHelper.queryHighSpeedSupport] returns `supports120fps = true`,
 *   LiveScreen can offer a "120fps mode" toggle. When enabled, this class replaces
 *   the CameraX VideoCapture path. The ImageAnalysis pipeline continues to run
 *   at normal FPS via a separate Surface.
 *
 * Limitations:
 *   - High-speed mode does NOT support simultaneous preview + analysis + video on
 *     all devices. Some devices support only video + preview in high-speed mode.
 *   - On devices where the constraint is not met, open() will fail gracefully.
 *   - Audio is handled separately because MediaRecorder conflicts with the
 *     constrained session on some devices.
 *
 * STATUS: STRUCTURE READY — Camera2 setup implemented.
 *         Full integration with LiveViewModel requires surfacing a callback
 *         into the LIVE state machine and testing on a real device.
 *
 * TODO:
 *   - Test on Pixel 6a / 7 (confirmed 120fps support)
 *   - Integrate CaptureSession callbacks with LiveViewModel state machine
 *   - Add audio muxing (separate AudioRecord → MediaMuxer)
 *   - Expose as optional mode toggle in LiveScreen settings bar
 */
class HighSpeedCameraSession(private val context: Context) {

    private var cameraDevice    : CameraDevice?   = null
    private var captureSession  : CameraConstrainedHighSpeedCaptureSession? = null
    private var mediaRecorder   : MediaRecorder?  = null
    private var backgroundThread: HandlerThread?  = null
    private var backgroundHandler: Handler?       = null
    private var outputFile      : File?           = null
    private var isRecording     : Boolean         = false

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open the rear camera in high-speed mode.
     *
     * @param targetFps   Desired FPS (120 or 240). Falls back to the highest
     *                    supported FPS if exact match not found.
     * @param onReady     Called on the handler thread when the session is ready.
     * @param onError     Called on any error. Caller should fall back to CameraX.
     */
    @SuppressLint("MissingPermission")
    fun open(
        targetFps: Int = 120,
        onReady  : () -> Unit,
        onError  : (String) -> Unit
    ) {
        startBackgroundThread()

        val capability = HighSpeedCameraHelper.queryHighSpeedSupport(context)
        if (!capability.supports120fps) {
            onError("Device does not support high-speed capture")
            return
        }

        // Pick best resolution at target FPS
        val bestRange = capability.highFpsRanges
            .filter { it.upper >= targetFps }
            .minByOrNull { it.upper }   // prefer lowest FPS that meets the request
        if (bestRange == null) {
            onError("No suitable FPS range for $targetFps fps")
            return
        }
        val resolution = capability.bestResolution ?: Size(1280, 720)

        // Find rear camera
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: run { onError("No rear camera found"); return }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                Log.d(TAG, "Camera opened for high-speed mode at ${bestRange.upper}fps")
                setupSession(device, resolution, bestRange, onReady, onError)
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
            override fun onError(device: CameraDevice, error: Int) {
                device.close(); cameraDevice = null
                onError("Camera error code $error")
            }
        }, backgroundHandler)
    }

    private fun setupSession(
        device    : CameraDevice,
        resolution: Size,
        fpsRange  : android.util.Range<Int>,
        onReady   : () -> Unit,
        onError   : (String) -> Unit
    ) {
        try {
            val dir    = File(context.filesDir, "sessions").also { it.mkdirs() }
            val ts     = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            outputFile = File(dir, "hs_putt_$ts.mp4")

            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder!!.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(resolution.width, resolution.height)
                setVideoFrameRate(fpsRange.upper)
                setVideoEncodingBitRate(8_000_000)   // 8 Mbps
                setOutputFile(outputFile!!.absolutePath)
                prepare()
            }

            val recorderSurface = mediaRecorder!!.surface
            val outputs         = listOf(OutputConfiguration(recorderSurface))

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_HIGH_SPEED,
                    outputs,
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session as? CameraConstrainedHighSpeedCaptureSession
                            if (captureSession == null) { onError("Session is not high-speed"); return }
                            Log.d(TAG, "High-speed session configured")
                            onReady()
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onError("Session configuration failed")
                        }
                    }
                )
                device.createCaptureSession(sessionConfig)
            } else {
                // API 26-27 fallback
                @Suppress("DEPRECATION")
                device.createConstrainedHighSpeedCaptureSession(
                    listOf(recorderSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session as? CameraConstrainedHighSpeedCaptureSession
                            onReady()
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onError("Session config failed")
                        }
                    },
                    backgroundHandler
                )
            }
        } catch (e: Exception) {
            onError("Session setup failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun startRecording() {
        if (isRecording) return
        val session = captureSession ?: return
        val device  = cameraDevice  ?: return
        try {
            val recorderSurface = mediaRecorder!!.surface
            val requestBuilder  = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder.addTarget(recorderSurface)
            val requests = session.createHighSpeedRequestList(requestBuilder.build())
            session.setRepeatingBurst(requests, null, backgroundHandler)
            mediaRecorder!!.start()
            isRecording = true
            Log.d(TAG, "High-speed recording started")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null
        return try {
            captureSession?.stopRepeating()
            mediaRecorder?.stop()
            isRecording = false
            val path = outputFile?.absolutePath
            Log.d(TAG, "High-speed recording saved: $path")
            path
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording failed", e)
            null
        }
    }

    fun close() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            mediaRecorder?.release()
        } catch (e: Exception) { Log.w(TAG, "close error", e) } finally {
            captureSession = null
            cameraDevice   = null
            mediaRecorder  = null
            stopBackgroundThread()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("HighSpeedCamera").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread  = null
        backgroundHandler = null
    }

    val isOpen get() = cameraDevice != null
}
