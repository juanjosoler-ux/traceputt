package com.puttvision.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Detects putter-ball impact via microphone RMS energy spike.
 *
 * Reads PCM 16-bit mono at 44100 Hz in ~10ms windows. When RMS exceeds
 * [impactThreshold], emits a timestamp on [impactEvents].
 *
 * Integrated with [AdaptiveAudioThreshold]: if [useAdaptiveThreshold] is true,
 * the threshold is automatically calibrated from ambient noise in the first
 * ~2 seconds of each session. Falls back to [impactThreshold] if not calibrated.
 *
 * STATUS: FUNCTIONAL.
 */
@Singleton
class AudioImpactDetector @Inject constructor(
    private val adaptiveThreshold: AdaptiveAudioThreshold
) {
    companion object {
        private const val SAMPLE_RATE          = 44_100
        private const val CHANNEL_CONFIG       = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT         = AudioFormat.ENCODING_PCM_16BIT
        private const val WINDOW_SAMPLES       = 441
        private const val DEFAULT_RMS_THRESHOLD= 3_500f
        private const val REARM_DELAY_MS       = 1_200L
    }

    /** Manual override threshold — updated from SettingsViewModel */
    var impactThreshold: Float = DEFAULT_RMS_THRESHOLD

    /** When true, adaptive calibration overrides [impactThreshold] */
    var useAdaptiveThreshold: Boolean = true

    private val _impactEvents = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val impactEvents: SharedFlow<Long> = _impactEvents.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob : Job?         = null
    private var isArmed    : Boolean      = false

    val isRunning: Boolean get() = captureJob?.isActive == true

    private val effectiveThreshold: Float
        get() = if (useAdaptiveThreshold && adaptiveThreshold.isCalibrated)
            adaptiveThreshold.computedThreshold
        else
            impactThreshold

    // ─────────────────────────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun start(scope: CoroutineScope) {
        if (isRunning) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR_BAD_VALUE) return
        val bufSize = maxOf(minBuf, WINDOW_SAMPLES * 2 * 4)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release(); audioRecord = null; return
        }
        audioRecord!!.startRecording()
        isArmed = true

        // Begin adaptive calibration
        adaptiveThreshold.startCalibration()

        captureJob = scope.launch(Dispatchers.IO) { processAudio() }
    }

    suspend fun stop() {
        captureJob?.cancelAndJoin()
        captureJob = null
        withContext(Dispatchers.IO) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
        isArmed = false
    }

    fun disarm() { isArmed = false }
    fun rearm()  { isArmed = true  }

    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun processAudio() = withContext(Dispatchers.IO) {
        val buffer = ShortArray(WINDOW_SAMPLES)
        while (isActive) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (read <= 0) continue

            val rms = computeRms(buffer, read)

            // Feed to adaptive calibrator while it's learning
            adaptiveThreshold.onRmsSample(rms)

            if (!isArmed) continue

            if (rms >= effectiveThreshold) {
                val ts = System.currentTimeMillis()
                _impactEvents.tryEmit(ts)
                isArmed = false
                delay(REARM_DELAY_MS)
                isArmed = true
            }
        }
    }

    private fun computeRms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) { val s = samples[i].toDouble(); sum += s * s }
        return sqrt(sum / count).toFloat()
    }
}
