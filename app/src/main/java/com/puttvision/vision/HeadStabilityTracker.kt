package com.puttvision.vision

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Tracks head stability throughout the putting stroke using the nose landmark.
 *
 * Workflow:
 *  1. Call [startTracking] when stroke begins (READY -> BUFFERING).
 *     Reference point is captured lazily on the FIRST valid nose landmark.
 *  2. Call [update] on every frame during BUFFERING + post-impact.
 *  3. Call [finalize] after follow-through ends to get the result.
 *
 * STATUS: FUNCTIONAL — no longer requires a Pose object at start.
 */
class HeadStabilityTracker @Inject constructor() {

    private var referenceX       : Float   = -1f   // -1 = not yet captured
    private var referenceY       : Float   = -1f
    private var maxDisplacementPx: Float   = 0f
    private var frameCount       : Int     = 0
    private var sumDisplacement  : Float   = 0f
    private var imageWidthPx     : Float   = 1f
    private var isTracking       : Boolean = false

    /**
     * Begin tracking for a new putt. Reference nose position is captured
     * lazily on the first [update] call that returns a valid landmark.
     */
    fun startTracking(imageWidth: Int) {
        imageWidthPx      = imageWidth.toFloat()
        referenceX        = -1f
        referenceY        = -1f
        maxDisplacementPx = 0f
        frameCount        = 0
        sumDisplacement   = 0f
        isTracking        = true
    }

    /** Feed each analysis frame here while BUFFERING/IMPACT_DETECTED. */
    fun update(pose: Pose) {
        if (!isTracking) return
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE) ?: return

        if (referenceX < 0f) {
            referenceX = nose.position.x
            referenceY = nose.position.y
            return
        }

        val dx   = nose.position.x - referenceX
        val dy   = nose.position.y - referenceY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > maxDisplacementPx) maxDisplacementPx = dist
        sumDisplacement += dist
        frameCount++
    }

    /** Call after follow-through ends. Resets state automatically. */
    fun finalize(): HeadStabilityResult {
        if (!isTracking || frameCount == 0) {
            reset()
            return HeadStabilityResult(maxPx = 0f, avgPx = 0f, score = null)
        }
        val avgPx  = sumDisplacement / frameCount
        val maxPct = (maxDisplacementPx / imageWidthPx) * 100f
        val score  = ((5f - maxPct.coerceIn(0f, 5f)) / 5f * 100f).coerceIn(0f, 100f)
        reset()
        return HeadStabilityResult(maxPx = maxDisplacementPx, avgPx = avgPx, score = score)
    }

    fun reset() {
        referenceX = -1f; referenceY = -1f
        maxDisplacementPx = 0f; frameCount = 0; sumDisplacement = 0f
        isTracking = false
    }

    val isActive: Boolean get() = isTracking
}

data class HeadStabilityResult(
    val maxPx: Float,
    val avgPx: Float,
    val score: Float?   // 0-100, null if insufficient data
)
