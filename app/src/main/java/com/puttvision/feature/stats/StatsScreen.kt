package com.puttvision.feature.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puttvision.core.domain.model.Session
import com.puttvision.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsScreen(
    sessionId : Long?,
    onExport  : (Intent) -> Unit = {},
    vm        : StatsViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Consume one-shot export intent
    LaunchedEffect(state.exportIntent) {
        state.exportIntent?.let { intent ->
            onExport(intent)
            vm.onExportConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("STATS", style = MaterialTheme.typography.headlineMedium.copy(
                    color = PuttGreen, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
                state.session?.let { s ->
                    Text(
                        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                            .format(Date(s.createdAt)),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            }

            // Export buttons
            if (state.session != null) {
                if (state.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                        color    = PuttGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    // PDF
                    IconButton(onClick = { vm.exportPdf() }) {
                        Icon(Icons.Filled.PictureAsPdf, "Exportar PDF", tint = TextSecondary)
                    }
                    // CSV
                    IconButton(onClick = { vm.exportCsv() }) {
                        Icon(Icons.Filled.Share, "Exportar CSV", tint = TextSecondary)
                    }
                }
            }

            if (state.allSessions.size > 1) {
                SessionPicker(state.allSessions, state.session) { vm.switchSession(it.id) }
            }
        }

        HorizontalDivider(color = SurfaceHigh)

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                CircularProgressIndicator(color = PuttGreen)
            }
            return@Column
        }

        if (state.session == null) {
            Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                Text("Sin sesiones todavía.\nInicia tu primera sesión LIVE.",
                    style     = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                    textAlign = TextAlign.Center)
            }
            return@Column
        }

        val s = state.stats
        Spacer(Modifier.height(20.dp))

        // ── Summary tiles ─────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.spacedBy(12.dp)) {
            SummaryTile("PUTTS",  "${s.totalPutts}", TextPrimary, Modifier.weight(1f))
            SummaryTile("BUENOS", "${s.goodPutts}",  PuttGreen,   Modifier.weight(1f))
            SummaryTile("MALOS",  "${s.badPutts}",   ErrorRed,    Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))

        // ── Averages ──────────────────────────────────────────────────────────
        SectionLabel("PROMEDIOS")
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AvgRow("Tempo ratio",    s.avgTempoRatio?.let    { "%.2f".format(it) } ?: "—", "Ideal: 2.0 – 2.6", MetricTempo)
            AvgRow("Head stability", s.avgHeadStability?.let { "%.0f / 100".format(it) } ?: "—", "Ideal: > 85", MetricHead)
            AvgRow("Face angle",     s.avgFaceAngle?.let     { "%.1f°".format(it) } ?: "—", "Ideal: 0.0°", MetricFace)
        }

        Spacer(Modifier.height(28.dp))

        // ── Sparklines ────────────────────────────────────────────────────────
        if (s.tempoSeries.size >= 3) {
            SectionLabel("EVOLUCIÓN TEMPO")
            Spacer(Modifier.height(10.dp))
            Sparkline(s.tempoSeries, MetricTempo, 1.8f, 2.6f,
                Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 16.dp))
            AxisLabels(s.tempoSeries, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))
        }

        if (s.headSeries.size >= 3) {
            SectionLabel("EVOLUCIÓN HEAD STABILITY")
            Spacer(Modifier.height(10.dp))
            Sparkline(s.headSeries, MetricHead, 80f, 100f,
                Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 16.dp))
            AxisLabels(s.headSeries, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))
        }

        if (s.faceSeries.size >= 3) {
            SectionLabel("EVOLUCIÓN FACE ANGLE")
            Spacer(Modifier.height(10.dp))
            Sparkline(s.faceSeries, MetricFace, -2f, 2f,
                Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 16.dp))
            AxisLabels(s.faceSeries, Modifier.padding(horizontal = 16.dp)) { "%.1f°".format(it) }
            Spacer(Modifier.height(24.dp))
        }

        if (s.pathSeries.size >= 3) {
            SectionLabel("EVOLUCIÓN PATH")
            Spacer(Modifier.height(10.dp))
            Sparkline(s.pathSeries, MetricPath, -3f, 3f,
                Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 16.dp))
            AxisLabels(s.pathSeries, Modifier.padding(horizontal = 16.dp)) { "%.1f°".format(it) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionPicker(sessions: List<Session>, current: Session?, onSelect: (Session) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(8.dp)) {
            Text(current?.let { fmt.format(Date(it.createdAt)) } ?: "Sesión",
                style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary))
            Icon(Icons.Filled.ArrowDropDown, null, tint = TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { s ->
                DropdownMenuItem(
                    text    = { Column {
                        Text(fmt.format(Date(s.createdAt)), color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(s.perspectiveType.label, color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall)
                    }},
                    onClick = { onSelect(s); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary, letterSpacing = 2.sp))
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(12.dp), SurfaceMid) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.displayLarge.copy(color = color, fontSize = 34.sp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        }
    }
}

@Composable
private fun AvgRow(label: String, value: String, ideal: String, color: Color) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), SurfaceMid) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                Text(ideal, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(
                color = color, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun AxisLabels(values: List<Float>, modifier: Modifier, fmt: (Float) -> String = { "%.2f".format(it) }) {
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    Row(modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text("min ${fmt(min)}", style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
        Text("max ${fmt(max)}", style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, idealMin: Float, idealMax: Float, modifier: Modifier) {
    if (values.size < 2) return
    val minV  = minOf(values.min(), idealMin)
    val maxV  = maxOf(values.max(), idealMax)
    val range = (maxV - minV).coerceAtLeast(0.01f)

    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun yFor(v: Float) = h - ((v - minV) / range) * h

        drawRect(PuttGreen.copy(alpha = 0.10f),
            topLeft = Offset(0f, yFor(idealMax)),
            size    = Size(w, yFor(idealMin) - yFor(idealMax)))

        listOf(idealMin, idealMax).forEach { iv ->
            drawLine(PuttGreen.copy(0.25f), Offset(0f, yFor(iv)), Offset(w, yFor(iv)), 1.dp.toPx())
        }

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.size - 1) * w; val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            drawCircle(color, 3.5.dp.toPx(),
                Offset(i.toFloat() / (values.size - 1) * w, yFor(v)))
        }
    }
}
