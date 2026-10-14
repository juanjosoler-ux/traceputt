package com.puttvision.core.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// Training environment
// ─────────────────────────────────────────────────────────────────────────────

enum class EnvironmentType(val label: String) {
    GREEN("Green"),
    HOME ("Home / Carpet")
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera perspective for this session
// ─────────────────────────────────────────────────────────────────────────────

enum class PerspectiveType(val label: String, val description: String) {
    BEHIND  ("Behind",  "Path · Ball launch"),
    FRONT   ("Front",   "Face angle · Head stability"),
    LATERAL ("Lateral", "Tempo · Stroke dynamics")
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE session state machine
// ─────────────────────────────────────────────────────────────────────────────

enum class LiveSessionState {
    /** Camera just opened, no session running */
    IDLE,

    /** Waiting for player to reach address position */
    SEARCHING_ADDRESS,

    /** Address detected, waiting for stroke */
    READY,

    /** Stroke started, filling circular buffer */
    BUFFERING,

    /** Impact event received (audio / visual) */
    IMPACT_DETECTED,

    /** Running metrics analysis on captured frames */
    ANALYZING,

    /** Putt saved to DB; brief confirmation state */
    SAVED,

    /** Unrecoverable camera / analysis error */
    ERROR
}

// ─────────────────────────────────────────────────────────────────────────────
// Quality tag the user can apply in LAB
// ─────────────────────────────────────────────────────────────────────────────

enum class PuttQuality {
    UNTAGGED,
    GOOD,
    BAD
}
