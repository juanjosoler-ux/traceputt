package com.puttvision.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puttvision.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Multi-session evolution sparkline.
 * Each [MultiSessionPoint] is one session's average metric value.
 * Dots are green when inside the ideal range, metric color otherwise.
 * Shows: average badge, trend arrow, ideal band shading.
 */
@Composable
fun MultiSessionChart(
    points  : List<MultiSessionPoint>,
    label   : String,
    color   : Color,
    idealMin: Float,
    idealMax: Float,
    unit    : String   = "",
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val values = points.map { it.value }
    val minV   = minOf(values.min(), idealMin)
    val maxV   = maxOf(values.max(), idealMax)
    val range  = (maxV - minV).coerceAtLeast(0.01f)
    val avg    = values.average().toFloat()
    val trend  = if (values.size >= 2) (values.last() - values.first()) / values.size else 0f

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(
                color = TextSecondary, letterSpacing = 2.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
                    Text("Ø ${"%.2f".format(avg)}$unit",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold))
                }
                val (arrow, tc) = when {
                    abs(trend) < 0.005f -> "→" to TextSecondary
                    else -> if (trend > 0) "↑" to PuttGreen else "↓" to ErrorRed
                }
                Text(arrow, style = MaterialTheme.typography.bodyMedium.copy(color = tc, fontWeight = FontWeight.Bold))
            }
        }

        Spacer(Modifier.height(6.dp))

        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width; val h = size.height
            fun yFor(v: Float) = h - ((v - minV) / range) * h
            fun xFor(i: Int)   = if (points.size > 1) i.toFloat() / (points.size - 1) * w else w / 2

            // Ideal band
            drawRect(PuttGreen.copy(0.09f),
                topLeft = Offset(0f, yFor(idealMax)),
                size    = Size(w, yFor(idealMin) - yFor(idealMax)))
            listOf(idealMin, idealMax).forEach { iv ->
                drawLine(PuttGreen.copy(0.22f), Offset(0f, yFor(iv)), Offset(w, yFor(iv)), 1.dp.toPx())
            }

            val path = Path()
            points.forEachIndexed { i, pt ->
                val x = xFor(i); val y = yFor(pt.value)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            points.forEachIndexed { i, pt ->
                val x = xFor(i); val y = yFor(pt.value)
                val inIdeal = pt.value in idealMin..idealMax
                drawCircle(if (inIdeal) PuttGreen else color, 4.dp.toPx(), Offset(x, y))
                drawCircle(Color.Black.copy(0.25f), 2.dp.toPx(), Offset(x, y))
            }
        }

        Spacer(Modifier.height(4.dp))
        val dateFmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(dateFmt.format(Date(points.first().dateMs)),
                style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
            Text("${points.size} sesiones",
                style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
            Text(dateFmt.format(Date(points.last().dateMs)),
                style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
        }
    }
}

data class MultiSessionPoint(val dateMs: Long, val value: Float)
