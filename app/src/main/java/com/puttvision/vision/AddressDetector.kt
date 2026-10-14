package com.puttvision.vision

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import javax.inject.Inject

/**
 * Determines whether a player is in putting address position using ML Kit pose.
 *
 * Criteria (all must hold for [STABILITY_WINDOW_MS]):
 *  1. Key landmarks are visible (confidence ≥ [minConfidence])
 *  2. Both wrists are below the chin
 *  3. Body shows minimal movement (nose drift ≤ [maxNoseDriftNorm])
 *
 * Thresholds are adaptive:
 *  - [minConfidence] starts at 0.5, can be lowered to 0.35 for poor lighting
 *  - [maxNoseDriftNorm] starts at 0.025 (2.5% of frame width), relaxed automatically
 *    after [ADAPTATION_THRESHOLD] failed attempts (player struggling to hold still)
 *
 * STATUS: FUNCTIONAL — adaptive sensitivity added.
 * TODO: Add hip-bend angle check for correct putting posture (not just standing upright).
 */
class AddressDetector @Inject constructor() {

    companion object {
        private const val STABILITY_WINDOW_MS     = 1_200L
        private const val DEFAULT_MIN_CONFIDENCE  = 0.5f
        private const val RELAXED_MIN_CONFIDENCE  = 0.35f
        private const val DEFAULT_MAX_NOSE_DRIFT  = 0.025f
        private const val RELAXED_MAX_NOSE_DRIFT  = 0.040f
        /** After this many consecutive failed attempts, relax thresholds */
        private const val ADAPTATION_THRESHOLD    = 8
    }

    // Adaptive thresholds
    private var minConfidence : Float = DEFAULT_MIN_CONFIDENCE
    private var maxNoseDrift  : Float = DEFAULT_MAX_NOSE_DRIFT

    // Stability tracking
    private val recentNoseX = ArrayDeque<Float>(30)
    private val recentNoseY = ArrayDeque<Float>(30)
    private var stableStartMs   : Long = 0L
    private var consecutiveFails: Int  = 0

    // ─────────────────────────────────────────────────────────────────────────

    fun analyze(pose: Pose?, timestampMs: Long): AddressResult {
        if (pose == null) {
            consecutiveFails++
            adaptThresholds()
            stableStartMs = 0L
            return AddressResult.NotVisible
        }
        val nose      = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist= pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip   = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip  = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)

        // ── 1. Visibility check ──────────────────────────────────────────────
        val landmarks = listOfNotNull(nose, leftWrist, rightWrist, leftHip, rightHip)
        if (landmarks.size < 5 || landmarks.any { it.inFrameLikelihood < minConfidence }) {
            consecutiveFails++
            adaptThresholds()
            stableStartMs = 0L
            return AddressResult.NotVisible
        }

        nose!!; leftWrist!!; rightWrist!!; leftHip!!; rightHip!!

        // ── 2. Wrists below chin ─────────────────────────────────────────────
        val chinY = nose.position.y + 30f
        if (leftWrist.position.y < chinY || rightWrist.position.y < chinY) {
            consecutiveFails++
            adaptThresholds()
            stableStartMs = 0L
            return AddressResult.WristsNotDown
        }

        // ── 3. Body stability ────────────────────────────────────────────────
        val nx = nose.position.x
        val ny = nose.position.y

        if (recentNoseX.size >= 30) { recentNoseX.removeFirst(); recentNoseY.removeFirst() }
        recentNoseX.addLast(nx); recentNoseY.addLast(ny)

        if (recentNoseX.size < 5) return AddressResult.Searching

        // Normalise drift by approximate shoulder width for resolution-independence
        val shoulderRef = leftShoulder?.let { abs(it.position.x - leftHip.position.x) } ?: 80f
        val norm = shoulderRef.coerceAtLeast(40f)
        val driftNorm = ((recentNoseX.max() - recentNoseX.min()) +
                         (recentNoseY.max() - recentNoseY.min())) / norm

        if (driftNorm > maxNoseDrift) {
            consecutiveFails++
            adaptThresholds()
            stableStartMs = timestampMs
            return AddressResult.Searching
        }

        // ── 4. Stable for required window ────────────────────────────────────
        if (stableStartMs == 0L) stableStartMs = timestampMs
        consecutiveFails = 0
        // Reset to default thresholds on success
        minConfidence = DEFAULT_MIN_CONFIDENCE
        maxNoseDrift  = DEFAULT_MAX_NOSE_DRIFT

        val held = timestampMs - stableStartMs
        return if (held >= STABILITY_WINDOW_MS) {
            AddressResult.Confirmed
        } else {
            AddressResult.Stabilising(held.toFloat() / STABILITY_WINDOW_MS)
        }
    }

    /** Gradually relax detection thresholds when the player struggles. */
    private fun adaptThresholds() {
        if (consecutiveFails >= ADAPTATION_THRESHOLD) {
            minConfidence = RELAXED_MIN_CONFIDENCE
            maxNoseDrift  = RELAXED_MAX_NOSE_DRIFT
        }
    }

    fun reset() {
        recentNoseX.clear(); recentNoseY.clear()
        stableStartMs    = 0L
        consecutiveFails = 0
        minConfidence    = DEFAULT_MIN_CONFIDENCE
        maxNoseDrift     = DEFAULT_MAX_NOSE_DRIFT
    }

    // Exposed for unit tests / debug overlay
    val currentMaxDrift    get() = maxNoseDrift
    val currentMinConf     get() = minConfidence
    val failCount          get() = consecutiveFails
}

private fun abs(f: Float) = if (f < 0) -f else f

// ─────────────────────────────────────────────────────────────────────────────

sealed class AddressResult {
    object NotVisible    : AddressResult()
    object WristsNotDown : AddressResult()
    object Searching     : AddressResult()
    data class Stabilising(val progress: Float) : AddressResult()
    object Confirmed     : AddressResult()
}
