package com.puttvision.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Lightweight microphone level monitor that emits normalised RMS amplitude
 * (0f–1f) continuously so the UI can draw a VU meter.
 *
 * This is separate from [AudioImpactDetector] so it can run even when the
 * impact detector is disarmed (e.g. in ANALYZING / SAVED states) to give
 * continuous visual feedback that the microphone is working.
 *
 * Emits every ~50ms (one window of 2205 samples at 44100 Hz).
 *
 * STATUS: FUNCTIONAL.
 */
@Singleton
class AudioLevelMonitor @Inject constructor() {

    companion object {
        private const val SAMPLE_RATE   = 44_100
        private const val WINDOW_SAMPLES= 2_205   // ~50ms
        /** Reference amplitude for normalisation. Approx. max speech RMS. */
        private const val REF_AMPLITUDE = 6_000f
    }

    private val _level = MutableStateFlow(0f)
    /** Normalised microphone level 0..1. Emits ~20x per second. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private var record   : AudioRecord? = null
    private var monitorJob: Job?        = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope) {
        if (monitorJob?.isActive == true) return
        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(WINDOW_SAMPLES * 2)

        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        if (record?.state != AudioRecord.STATE_INITIALIZED) {
            record?.release(); record = null; return
        }
        record!!.startRecording()
        monitorJob = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(WINDOW_SAMPLES)
            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read > 0) {
                    val rms       = rms(buf, read)
                    val normalised= (rms / REF_AMPLITUDE).coerceIn(0f, 1f)
                    _level.value  = normalised
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        record?.stop()
        record?.release()
        record = null
        _level.value = 0f
    }

    private fun rms(buf: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val s = buf[i].toDouble(); sum += s * s }
        return sqrt(sum / n).toFloat()
    }
}
