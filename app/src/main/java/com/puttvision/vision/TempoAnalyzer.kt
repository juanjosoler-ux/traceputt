package com.puttvision.vision

import javax.inject.Inject

/**
 * Calculates putting tempo from three timestamp events:
 *
 *   STROKE_START  →  IMPACT  →  FOLLOW_END
 *
 * Standard putting tempo target: 2:1 ratio (backswing twice as long as follow-through).
 * Tour average is approximately 2.0 – 2.5.
 *
 * STATUS: FUNCTIONAL — timestamps from audio impact + wrist-velocity heuristic.
 * TODO: Replace STROKE_START detection with wrist-velocity threshold from ML Kit.
 *       Currently STROKE_START is triggered externally by LiveViewModel when
 *       the player leaves address (body movement above threshold).
 */
class TempoAnalyzer @Inject constructor() {

    private var strokeStartMs  : Long? = null
    private var impactMs       : Long? = null
    private var followEndMs    : Long? = null

    /** Call when the putter starts moving (leaving address) */
    fun onStrokeStart(timestampMs: Long) {
        strokeStartMs  = timestampMs
        impactMs       = null
        followEndMs    = null
    }

    /** Call when impact is detected (audio trigger or visual) */
    fun onImpact(timestampMs: Long) {
        impactMs = timestampMs
    }

    /**
     * Call when follow-through motion has settled.
     * This is currently triggered ~600 ms after impact by a timer in LiveViewModel.
     * TODO: Detect dynamically via wrist deceleration.
     */
    fun onFollowEnd(timestampMs: Long) {
        followEndMs = timestampMs
    }

    /**
     * Compute final tempo result.
     * Returns null if insufficient timestamps were recorded.
     */
    fun finalize(): TempoResult? {
        val start  = strokeStartMs ?: return null
        val impact = impactMs      ?: return null
        val end    = followEndMs   ?: return null  // do not invent an endpoint

        val backswingMs  = (impact - start).coerceAtLeast(1)
        val followMs     = (end    - impact).coerceAtLeast(1)
        val ratio        = backswingMs.toFloat() / followMs.toFloat()

        reset()
        return TempoResult(
            backswingMs  = backswingMs,
            followMs     = followMs,
            ratio        = ratio,
            quality      = classifyTempo(ratio)
        )
    }

    private fun classifyTempo(ratio: Float): TempoQuality = when {
        ratio < 1.4f -> TempoQuality.TOO_FAST_BACKSWING   // unusually short backswing
        ratio < 1.8f -> TempoQuality.SLIGHTLY_RUSHED
        ratio < 2.6f -> TempoQuality.GOOD                  // sweet spot 2:1
        ratio < 3.2f -> TempoQuality.SLIGHTLY_SLOW
        else         -> TempoQuality.TOO_SLOW_BACKSWING
    }

    fun reset() {
        strokeStartMs = null
        impactMs      = null
        followEndMs   = null
    }

    val hasStrokeStart get() = strokeStartMs != null
    val hasImpact      get() = impactMs      != null
}

// ─────────────────────────────────────────────────────────────────────────────

data class TempoResult(
    val backswingMs: Long,
    val followMs   : Long,
    val ratio      : Float,
    val quality    : TempoQuality
)

enum class TempoQuality(val label: String) {
    TOO_FAST_BACKSWING ("Backswing demasiado rápido"),
    SLIGHTLY_RUSHED    ("Ligeramente acelerado"),
    GOOD               ("Tempo correcto ✓"),
    SLIGHTLY_SLOW      ("Ligeramente lento"),
    TOO_SLOW_BACKSWING ("Backswing demasiado lento")
}
