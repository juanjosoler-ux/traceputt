package com.puttvision.core.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// Training session (group of putts)
// ─────────────────────────────────────────────────────────────────────────────

data class Session(
    val id             : Long            = 0,
    val createdAt      : Long            = System.currentTimeMillis(),
    val environmentType: EnvironmentType = EnvironmentType.HOME,
    val perspectiveType: PerspectiveType = PerspectiveType.BEHIND,
    val notes          : String          = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Single captured putt within a session
// ─────────────────────────────────────────────────────────────────────────────

data class PuttRecord(
    val id                 : Long        = 0,
    val sessionId          : Long        = 0,
    val createdAt          : Long        = System.currentTimeMillis(),
    /** Absolute path to the saved MP4 clip, or null if not yet captured */
    val videoPath          : String?     = null,
    /** Absolute path to thumbnail image, or null */
    val thumbnailPath      : String?     = null,
    val quality            : PuttQuality = PuttQuality.UNTAGGED,
    /** 0f–1f confidence that detection was correct */
    val detectionConfidence: Float       = 0f
)

// ─────────────────────────────────────────────────────────────────────────────
// Computed metrics for a putt
// null = metric not yet computed or perspective doesn't support it
// ─────────────────────────────────────────────────────────────────────────────

data class PuttMetrics(
    val puttId        : Long  = 0,

    // ── Tempo ─────────────────────────────────────────────────────────────
    /** Backswing duration in milliseconds */
    val backswingMs   : Long? = null,
    /** Follow-through duration in milliseconds */
    val followMs      : Long? = null,
    /** Ratio backswing / follow-through.  Ideal ≈ 2:1 (= 2.0) */
    val tempoRatio    : Float? = null,

    // ── Head stability ────────────────────────────────────────────────────
    /** Max nose-landmark displacement in px during stroke */
    val headMovePx    : Float? = null,
    /** Stability score 0–100 (100 = perfectly still) */
    val headStability : Float? = null,

    // ── Face angle (OpenCV marker-based) ──────────────────────────────────
    /** Degrees off square at impact.  + = open, - = closed */
    val faceAngleDeg  : Float? = null,

    // ── Path ──────────────────────────────────────────────────────────────
    /** Degrees off target line.  + = outside-in, - = inside-out */
    val pathAngleDeg  : Float? = null,

    // ── Ball launch ───────────────────────────────────────────────────────
    /** Initial ball direction in degrees off target */
    val launchDirDeg  : Float? = null,

    // ── General ───────────────────────────────────────────────────────────
    /** 0f–1f overall confidence across all metrics */
    val overallConf   : Float  = 0f
)
