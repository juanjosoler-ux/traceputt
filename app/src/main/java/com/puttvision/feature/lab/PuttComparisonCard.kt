package com.puttvision.feature.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.ui.theme.*
import kotlin.math.abs

/**
 * Shows the current putt's metrics side-by-side with the session average,
 * with a visual delta indicator showing improvement or regression.
 *
 * Called from [PuttDetailScreen] when session average data is available.
 */
@Composable
fun PuttComparisonCard(
    current: PuttMetrics,
    avg    : SessionAverage,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "VS MEDIA DE SESIÓN",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = TextSecondary, letterSpacing = 2.sp
                )
            )
            Spacer(Modifier.height(12.dp))

            // Tempo comparison
            avg.tempoRatio?.let { avgVal ->
                current.tempoRatio?.let { cur ->
                    ComparisonRow(
                        label    = "TEMPO",
                        current  = "%.2f".format(cur),
                        avg      = "%.2f".format(avgVal),
                        delta    = cur - avgVal,
                        color    = MetricTempo,
                        higherIsBetter = false,   // 2.0 is ideal, further from ideal = worse
                        idealTarget    = 2.2f
                    )
                }
            }

            // Head stability comparison
            avg.headStability?.let { avgVal ->
                current.headStability?.let { cur ->
                    Spacer(Modifier.height(8.dp))
                    ComparisonRow(
                        label   = "HEAD",
                        current = "%.0f".format(cur),
                        avg     = "%.0f".format(avgVal),
                        delta   = cur - avgVal,
                        color   = MetricHead,
                        higherIsBetter = true
                    )
                }
            }

            // Face angle comparison
            avg.faceAngle?.let { avgVal ->
                current.faceAngleDeg?.let { cur ->
                    Spacer(Modifier.height(8.dp))
                    ComparisonRow(
                        label   = "FACE",
                        current = "%.1f°".format(cur),
                        avg     = "%.1f°".format(avgVal),
                        delta   = cur - avgVal,
                        color   = MetricFace,
                        higherIsBetter = false,
                        idealTarget    = 0f
                    )
                }
            }

            // Path angle comparison
            avg.pathAngle?.let { avgVal ->
                current.pathAngleDeg?.let { cur ->
                    Spacer(Modifier.height(8.dp))
                    ComparisonRow(
                        label   = "PATH",
                        current = "%.1f°".format(cur),
                        avg     = "%.1f°".format(avgVal),
                        delta   = cur - avgVal,
                        color   = MetricPath,
                        higherIsBetter = false,
                        idealTarget    = 0f
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data class for session averages passed to [PuttComparisonCard].
 * All fields are nullable — null means not enough data.
 */
data class SessionAverage(
    val tempoRatio   : Float?,
    val headStability: Float?,
    val faceAngle    : Float?,
    val pathAngle    : Float?
)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonRow(
    label         : String,
    current       : String,
    avg           : String,
    delta         : Float,
    color         : Color,
    higherIsBetter: Boolean,
    idealTarget   : Float? = null
) {
    // Determine improvement colour
    val isImprovement = if (idealTarget != null) {
        // Closer to ideal = improvement (tempo, face, path)
        abs(delta) < 0.01f   // negligible — neither
        // (simplified: just show neutral when we don't know direction)
        false
    } else {
        if (higherIsBetter) delta > 0.5f else delta < -0.5f
    }
    val isDegression = if (idealTarget != null) false
                       else if (higherIsBetter) delta < -0.5f else delta > 0.5f

    val deltaColor = when {
        isImprovement -> PuttGreen
        isDegression  -> ErrorRed
        else          -> TextSecondary
    }
    val deltaPrefix = if (delta >= 0f) "+" else ""
    val deltaStr    = when {
        abs(delta) < 0.005f -> "="
        else                -> "$deltaPrefix${"%.2f".format(delta)}"
    }

    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), SurfaceDeep) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(label,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                modifier = Modifier.width(48.dp))

            // Current value
            Text(current, style = MaterialTheme.typography.headlineSmall.copy(
                color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp))

            // Delta arrow bar
            DeltaBar(delta = delta, color = deltaColor, modifier = Modifier.width(60.dp).height(6.dp))

            // Average
            Column(horizontalAlignment = Alignment.End) {
                Text("media", style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
                Text(avg, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }

            // Delta label
            Text(deltaStr,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = deltaColor, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(44.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun DeltaBar(delta: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val barW = (abs(delta) / 3f).coerceIn(0f, 1f) * (w / 2f)

        // centre line
        drawLine(SurfaceHigh, Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx(), StrokeCap.Round)

        // delta bar
        if (barW > 1f) {
            val startX = if (delta >= 0f) cx else cx - barW
            drawLine(color, Offset(startX, h / 2), Offset(startX + barW, h / 2),
                h, StrokeCap.Round)
        }
        // centre dot
        drawCircle(color, h / 2, Offset(cx, h / 2))
    }
}
