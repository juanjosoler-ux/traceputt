package com.puttvision.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puttvision.core.domain.model.EnvironmentType
import com.puttvision.core.domain.model.PerspectiveType
import com.puttvision.core.domain.model.Session
import com.puttvision.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onStartSession : (Long) -> Unit,
    onOpenSettings : () -> Unit,
    onResumeSession: (Long) -> Unit = {},
    vm             : HomeViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdSessionId) {
        state.createdSessionId?.let { id ->
            vm.onSessionNavigated()
            onStartSession(id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            Arrangement.SpaceBetween,
            Alignment.Top
        ) {
            Column {
                Text("PUTT", style = MaterialTheme.typography.headlineMedium.copy(
                    color = PuttGreen, fontWeight = FontWeight.Black, letterSpacing = 4.sp))
                Text("VISION", style = MaterialTheme.typography.headlineMedium.copy(
                    color = TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 4.sp))
                Spacer(Modifier.height(2.dp))
                Text("Análisis de putting",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, "Ajustes", tint = TextSecondary)
            }
        }

        // ── Stats summary strip ───────────────────────────────────────────────
        if (state.stats.totalSessions > 0) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                RoundedCornerShape(12.dp),
                SurfaceMid
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    Arrangement.SpaceEvenly
                ) {
                    StatChip("Sesiones",   "${state.stats.totalSessions}", TextPrimary)
                    StatChip("Putts",      "${state.stats.totalPutts}",    TextPrimary)
                    state.stats.lastTempo?.let {
                        StatChip("Tempo (últ.)", "%.2f".format(it),
                            if (it in 1.8f..2.6f) PuttGreen else ImpactAmber)
                    }
                    state.stats.lastHeadScore?.let {
                        StatChip("Head (últ.)", "%.0f".format(it),
                            if (it >= 75f) PuttGreen else ImpactAmber)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier        = Modifier.weight(1f),
            contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Environment selector ──────────────────────────────────────────
            item {
                SectionLabel("ENTORNO")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EnvironmentType.entries.forEach { env ->
                        SelectionChip(
                            label    = env.label,
                            selected = state.environment == env,
                            modifier = Modifier.weight(1f),
                            onClick  = { vm.selectEnvironment(env) }
                        )
                    }
                }
            }

            // ── Perspective selector ──────────────────────────────────────────
            item {
                SectionLabel("PERSPECTIVA")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerspectiveType.entries.forEach { p ->
                        PerspectiveRow(
                            perspective = p,
                            selected    = state.perspective == p,
                            onClick     = { vm.selectPerspective(p) }
                        )
                    }
                }
            }

            // ── Recent sessions ───────────────────────────────────────────────
            if (state.recentSessions.isNotEmpty()) {
                item { SectionLabel("SESIONES RECIENTES") }
                items(state.recentSessions.take(3)) { session ->
                    RecentSessionRow(session = session, onClick = { onResumeSession(session.id) })
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Start button ──────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick  = { vm.startSession() },
                enabled  = !state.isCreating,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PuttGreen, contentColor = Background)
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Background, strokeWidth = 2.dp)
                } else {
                    Text("NUEVA SESIÓN", style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black, fontSize = 16.sp))
                }
            }
            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(
            color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(
        color = TextSecondary, letterSpacing = 2.sp))
}

@Composable
private fun SelectionChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) PuttGreen else SurfaceHigh),
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PuttGreen.copy(alpha = 0.12f) else SurfaceDeep)
    ) {
        Text(label, color = if (selected) PuttGreen else TextSecondary,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PerspectiveRow(perspective: PerspectiveType, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) PuttGreen else SurfaceHigh),
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PuttGreen.copy(alpha = 0.12f) else SurfaceDeep)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(perspective.label.uppercase(),
                color = if (selected) PuttGreen else TextPrimary,
                style = MaterialTheme.typography.labelLarge)
            Text(perspective.description, color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun RecentSessionRow(session: Session, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = RoundedCornerShape(10.dp),
        color    = SurfaceDeep
    ) {
        Row(
            Modifier.padding(12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column {
                Text(fmt.format(Date(session.createdAt)),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                Text("${session.perspectiveType.label} · ${session.environmentType.label}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            }
            Text("▶", style = MaterialTheme.typography.bodyMedium.copy(color = PuttGreen))
        }
    }
}
