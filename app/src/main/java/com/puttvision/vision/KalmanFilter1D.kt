package com.puttvision.vision

/**
 * Simple 1-dimensional Kalman filter.
 *
 * Used by [PutterPathTracker] to smooth noisy marker centroid positions
 * before fitting the OLS regression line.
 *
 * Parameters:
 *  [processNoise]    (Q) – how much the true state can change per step.
 *                    Higher = trusts measurements more. Default: 1e-4
 *  [measurementNoise](R) – sensor noise variance.
 *                    Higher = trusts predictions more. Default: 0.1
 *
 * Usage:
 *   val kf = KalmanFilter1D()
 *   frames.forEach { raw -> val smoothed = kf.update(raw) }
 *
 * STATUS: FUNCTIONAL.
 */
class KalmanFilter1D(
    private val processNoise    : Double = 1e-4,
    private val measurementNoise: Double = 0.1
) {
    // State estimate
    private var x: Double = 0.0
    // Estimate error covariance
    private var p: Double = 1.0
    private var initialised: Boolean = false

    /**
     * Feed a new measurement and get the filtered estimate.
     */
    fun update(measurement: Float): Float {
        if (!initialised) {
            x = measurement.toDouble()
            initialised = true
            return measurement
        }

        // Predict
        val xPred = x
        val pPred = p + processNoise

        // Update (Kalman gain)
        val k     = pPred / (pPred + measurementNoise)
        x         = xPred + k * (measurement - xPred)
        p         = (1.0 - k) * pPred

        return x.toFloat()
    }

    fun reset() {
        x           = 0.0
        p           = 1.0
        initialised = false
    }
}
