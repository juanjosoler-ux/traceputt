package com.puttvision.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puttvision.BuildConfig
import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.ui.theme.*

/**
 * Debug-only overlay showing raw detector values while the LIVE camera is running.
 * Only rendered in DEBUG builds. Completely absent in release.
 *
 * Shows:
 *  - Session state
 *  - Address detection: fail count, current thresholds, progress
 *  - Audio: armed, level, adaptive threshold
 *  - Video: frame count in buffer
 *  - 120fps: enabled / not
 *
 * Usage: add [DebugOverlay] inside the Box in LiveScreen alongside other overlays.
 */
@Composable
fun DebugOverlay(
    uiState    : LiveUiState,
    modifier   : Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    val lines = buildList {
        add("STATE: ${uiState.sessionState.name}")
        add("PUTTS: ${uiState.puttCount}")
        add("─────────────────")
        add("ADDR progress: ${"%.2f".format(uiState.addressProgress)}")
        val addr = uiState.addressResult
        if (addr != null) add("ADDR result: ${addr.javaClass.simpleName}")
        add("─────────────────")
        add("AUDIO armed: ${uiState.isAudioArmed}")
        add("AUDIO level: ${"%.3f".format(uiState.audioLevel)}")
        add("─────────────────")
        add("HIGH-SPD enabled: ${uiState.highSpeedEnabled}")
        add("HIGH-SPD support: ${uiState.supportsHighSpeed}")
        add("─────────────────")
        val m = uiState.lastMetrics
        if (m != null) {
            add("Last TEMPO: ${m.tempoRatio?.let { "%.2f".format(it) } ?: "-"}")
            add("Last HEAD:  ${m.headStability?.let { "%.1f".format(it) } ?: "-"}")
            add("Last FACE:  ${m.faceAngleDeg?.let  { "%.1f°".format(it) } ?: "-"}")
            add("Last PATH:  ${m.pathAngleDeg?.let  { "%.1f°".format(it) } ?: "-"}")
            add("Last LAUNCH:${m.launchDirDeg?.let  { "%.1f°".format(it) } ?: "-"}")
            add("Confidence: ${"%.0f%%".format(m.overallConf * 100)}")
        }
    }

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        color    = Background.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                "◆ DEBUG",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PuttGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = if (line.startsWith("─")) TextDisabled else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 9.sp
                    )
                )
            }
        }
    }
}
