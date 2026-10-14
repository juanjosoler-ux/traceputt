package com.puttvision.feature.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.ui.theme.*

/**
 * Compact vertical VU meter with calibration status badge.
 *
 * Visual states:
 *  - Calibrating: amber pulsing "◐" icon
 *  - Calibrated:  green "✓" + threshold value
 *  - Disarmed:    grey "✗"
 */
@Composable
fun VuMeterOverlay(
    level              : Float,
    isArmed            : Boolean,
    isCalibrated       : Boolean,
    effectiveThreshold : Float,
    sessionState       : LiveSessionState,
    modifier           : Modifier = Modifier
) {
    val animLevel by animateFloatAsState(
        targetValue   = level,
        animationSpec = tween(durationMillis = 80),
        label         = "vuLevel"
    )

    val show = sessionState != LiveSessionState.IDLE && sessionState != LiveSessionState.ERROR
    if (!show) return

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Status icon
        val (icon, color) = when {
            !isArmed     -> "✗" to TextDisabled
            isCalibrated -> "✓" to PuttGreen
            else         -> "◐" to ImpactAmber
        }
        Text(icon, style = MaterialTheme.typography.labelSmall.copy(color = color, fontSize = 10.sp))

        // Vertical bar
        Canvas(Modifier.width(12.dp).height(60.dp)) {
            val w = size.width; val h = size.height
            val radius = CornerRadius(w / 2, w / 2)

            // Track
            drawRoundRect(color = SurfaceHigh, size = Size(w, h), cornerRadius = radius)

            // Fill
            val filledH = (animLevel * h).coerceIn(1f, h)
            val barColor = when {
                animLevel >= 0.75f -> ErrorRed
                animLevel >= 0.40f -> ImpactAmber
                else               -> PuttGreen
            }.copy(alpha = if (isArmed) 0.9f else 0.35f)
            drawRoundRect(
                color        = barColor,
                topLeft      = Offset(0f, h - filledH),
                size         = Size(w, filledH),
                cornerRadius = radius
            )

            // Threshold marker line — shows where impact detection fires
            // Normalise: threshold/REF_AMPLITUDE where REF=6000
            val markerY = h * (1f - (effectiveThreshold / 6_000f).coerceIn(0.1f, 0.95f))
            drawLine(
                color       = if (isCalibrated) PuttGreen.copy(0.6f) else TextDisabled.copy(0.5f),
                start       = Offset(0f, markerY),
                end         = Offset(w,  markerY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Threshold value (compact)
        if (isCalibrated) {
            Text(
                "${(effectiveThreshold / 1000).toInt()}k",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = PuttGreen,
                    fontSize = 8.sp
                )
            )
        } else if (isArmed) {
            Text(
                "cal…",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = ImpactAmber,
                    fontSize = 7.sp
                )
            )
        }
    }
}
