package com.puttvision.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ──────────────────────────────────────────────────────────────────
/** Primary accent: crisp white-green used in pro scoring overlays */
val PuttGreen      = Color(0xFF00E676)
val PuttGreenDim   = Color(0xFF00C853)

/** Impact / warning indicator */
val ImpactAmber    = Color(0xFFFFB300)

/** Error / bad putt */
val ErrorRed       = Color(0xFFFF1744)

/** Ready / confirmed */
val ConfirmBlue    = Color(0xFF40C4FF)

// ── Surface hierarchy ──────────────────────────────────────────────────────
val Background     = Color(0xFF0A0A0A)   // near-black; readable in full sun
val SurfaceDeep    = Color(0xFF111111)
val SurfaceMid     = Color(0xFF1C1C1E)   // iOS-dark-feel card background
val SurfaceHigh    = Color(0xFF2C2C2E)

// ── Text ──────────────────────────────────────────────────────────────────
val TextPrimary    = Color(0xFFEEEEEE)
val TextSecondary  = Color(0xFF888888)
val TextDisabled   = Color(0xFF444444)

// ── Metric colors used in LAB / STATS ─────────────────────────────────────
val MetricTempo    = Color(0xFF64B5F6)   // blue
val MetricHead     = Color(0xFFCE93D8)   // purple
val MetricFace     = Color(0xFF80CBC4)   // teal
val MetricPath     = Color(0xFFFFCC80)   // orange
val MetricLaunch   = Color(0xFFA5D6A7)   // soft green
