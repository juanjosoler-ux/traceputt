package com.puttvision.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.ui.theme.ConfirmBlue
import com.puttvision.ui.theme.ImpactAmber
import com.puttvision.ui.theme.PuttGreen

/**
 * Draws a simplified pose skeleton over the camera preview.
 *
 * Only draws the relevant landmarks for putting analysis:
 *  - Head (nose)
 *  - Shoulders
 *  - Hips
 *  - Wrists (putter grip)
 *
 * Colour encodes the session state:
 *  - SEARCHING_ADDRESS → amber  (looking for position)
 *  - READY             → green  (address confirmed)
 *  - BUFFERING         → orange (stroke in progress)
 *  - other             → blue
 *
 * [landmarks] is a list of [NormLandmark] in the same order as
 * `com.google.mlkit.vision.pose.PoseLandmark` constants (0..32).
 *
 * STATUS: FUNCTIONAL — relies on LiveViewModel publishing landmarks.
 */
@Composable
fun SkeletonOverlay(
    landmarks    : List<NormLandmark>,
    sessionState : LiveSessionState,
    modifier     : Modifier = Modifier
) {
    if (landmarks.size < 17) return  // not enough points to draw

    val lineColor = when (sessionState) {
        LiveSessionState.SEARCHING_ADDRESS -> ImpactAmber
        LiveSessionState.READY             -> PuttGreen
        LiveSessionState.BUFFERING         -> Color(0xFFFF6D00)
        else                               -> ConfirmBlue
    }.copy(alpha = 0.75f)
    val dotColor = lineColor.copy(alpha = 0.95f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun lm(idx: Int): Offset? {
            val lm = landmarks.getOrNull(idx) ?: return null
            if (lm.confidence < 0.4f) return null
            return Offset(lm.x * w, lm.y * h)
        }

        val strokeW = 2.5f * density

        // ── Connections ────────────────────────────────────────────────────
        //  Using ML Kit PoseLandmark index constants:
        //    0=nose  11=L_shoulder 12=R_shoulder
        //   13=L_elbow 14=R_elbow  15=L_wrist 16=R_wrist
        //   23=L_hip   24=R_hip

        val connections = listOf(
            // Torso
            11 to 12,  // shoulder – shoulder
            11 to 23,  // L shoulder – L hip
            12 to 24,  // R shoulder – R hip
            23 to 24,  // hip – hip
            // Arms
            11 to 13,  // L shoulder – L elbow
            13 to 15,  // L elbow – L wrist
            12 to 14,  // R shoulder – R elbow
            14 to 16,  // R elbow – R wrist
            // Head to shoulders (simplified spine)
            0  to 11,
            0  to 12
        )

        connections.forEach { (a, b) ->
            val pa = lm(a) ?: return@forEach
            val pb = lm(b) ?: return@forEach
            drawLine(lineColor, pa, pb, strokeW, StrokeCap.Round)
        }

        // ── Landmark dots ──────────────────────────────────────────────────
        val keyDots = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24)
        keyDots.forEach { idx ->
            val pos = lm(idx) ?: return@forEach
            // Wrists are larger — they indicate grip position
            val radius = if (idx == 15 || idx == 16) 7f * density else 4.5f * density
            drawCircle(dotColor, radius, pos)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: convert ML Kit Pose to List<NormLandmark> for the UI layer
// ─────────────────────────────────────────────────────────────────────────────

fun com.google.mlkit.vision.pose.Pose.toNormLandmarks(
    imageWidth : Int,
    imageHeight: Int
): List<NormLandmark> {
    return allPoseLandmarks.map { lm ->
        NormLandmark(
            x          = (lm.position.x / imageWidth).coerceIn(0f, 1f),
            y          = (lm.position.y / imageHeight).coerceIn(0f, 1f),
            confidence = lm.inFrameLikelihood
        )
    }
}
