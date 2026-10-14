package com.puttvision.audio

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val TAG = "AdaptiveThreshold"

/**
 * Learns the ambient noise floor during a brief calibration period at
 * session start, then sets an adaptive impact threshold based on it.
 *
 * Algorithm:
 *  1. At session start, collect [CALIBRATION_WINDOWS] RMS readings (~2 seconds).
 *  2. Compute the mean ambient RMS and its standard deviation.
 *  3. Set threshold = mean + [SIGMA_MULTIPLIER] × stddev, clamped to sane bounds.
 *
 * This means the detector automatically adjusts for:
 *  - Quiet indoor practice (low baseline → lower threshold → catches soft chips)
 *  - Noisy outdoor green (high baseline → higher threshold → ignores footsteps)
 *
 * Usage:
 *  - [startCalibration] at SEARCHING_ADDRESS entry
 *  - Feed each [AudioRecord] window via [onRmsSample]
 *  - [isCalibrated] becomes true after enough samples
 *  - [computedThreshold] gives the value to set on [AudioImpactDetector]
 *
 * STATUS: FUNCTIONAL.
 */
@Singleton
class AdaptiveAudioThreshold @Inject constructor() {

    companion object {
        /** Number of 10ms windows to collect for calibration (~2 seconds) */
        private const val CALIBRATION_WINDOWS = 200
        /** How many sigma above the ambient mean the impact threshold is set */
        private const val SIGMA_MULTIPLIER    = 5.0f
        /** Minimum threshold regardless of ambient noise */
        private const val MIN_THRESHOLD       = 800f
        /** Maximum threshold cap */
        private const val MAX_THRESHOLD       = 10_000f
        /** Default (used before calibration) */
        const val DEFAULT_THRESHOLD           = 3_500f
    }

    private val samples = mutableListOf<Float>()
    private var calibrating = false

    val isCalibrated: Boolean
        get() = samples.size >= CALIBRATION_WINDOWS

    /** The computed adaptive threshold, or DEFAULT if not yet calibrated. */
    val computedThreshold: Float
        get() {
            if (!isCalibrated) return DEFAULT_THRESHOLD
            val mean   = samples.average().toFloat()
            val stddev = stdDev(samples, mean)
            val thresh = mean + SIGMA_MULTIPLIER * stddev
            return thresh.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD).also {
                Log.d(TAG, "Adaptive threshold: mean=${mean.toInt()} σ=${stddev.toInt()} → threshold=${it.toInt()}")
            }
        }

    fun startCalibration() {
        samples.clear()
        calibrating = true
        Log.d(TAG, "Audio calibration started")
    }

    /**
     * Feed one RMS reading from AudioRecord.
     * Stops collecting once [CALIBRATION_WINDOWS] samples are gathered.
     */
    fun onRmsSample(rms: Float) {
        if (!calibrating || isCalibrated) return
        samples.add(rms)
        if (isCalibrated) {
            calibrating = false
            Log.d(TAG, "Calibration complete: threshold=${computedThreshold.toInt()}")
        }
    }

    fun reset() {
        samples.clear()
        calibrating = false
    }

    private fun stdDev(values: List<Float>, mean: Float): Float {
        if (values.size < 2) return 0f
        val variance = values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size
        return Math.sqrt(variance).toFloat()
    }
}
