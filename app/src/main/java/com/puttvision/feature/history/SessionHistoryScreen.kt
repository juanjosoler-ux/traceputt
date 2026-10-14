package com.puttvision.feature.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.Session
import com.puttvision.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

data class SessionSummary(
    val session   : Session,
    val puttCount : Int,
    val avgTempo  : Float?,
    val avgHead   : Float?,
    val avgFace   : Float?,
    val avgPath   : Float?,
    val goodCount : Int,
    val badCount  : Int
)

data class HistoryUiState(
    val sessions          : List<SessionSummary>      = emptyList(),
    // Multi-session trend data
    val tempoTrend        : List<MultiSessionPoint>   = emptyList(),
    val headTrend         : List<MultiSessionPoint>   = emptyList(),
    val isLoading         : Boolean                   = true,
    val error             : String?                   = null
)

@HiltViewModel
class SessionHistoryViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val puttRepo   : PuttRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            sessionRepo.observeAll()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { sessions ->
                    val summaries = sessions.map { s -> buildSummary(s) }

                    // Build multi-session trend (chronological)
                    val ordered   = summaries.reversed()
                    val tempoTrend = ordered.filter { it.avgTempo != null }
                        .map { MultiSessionPoint(it.session.createdAt, it.avgTempo!!) }
                    val headTrend  = ordered.filter { it.avgHead  != null }
                        .map { MultiSessionPoint(it.session.createdAt, it.avgHead!!) }

                    _uiState.update {
                        it.copy(
                            sessions   = summaries,
                            tempoTrend = tempoTrend,
                            headTrend  = headTrend,
                            isLoading  = false
                        )
                    }
                }
        }
    }

    private suspend fun buildSummary(s: Session): SessionSummary {
        val putts = puttRepo.getBySession(s.id)
        return SessionSummary(
            session   = s,
            puttCount = putts.size,
            avgTempo  = puttRepo.avgTempoRatio(s.id),
            avgHead   = puttRepo.avgHeadStability(s.id),
            avgFace   = puttRepo.avgFaceAngle(s.id),
            avgPath   = puttRepo.avgPathAngle(s.id),
            goodCount = putts.count { it.quality.name == "GOOD" },
            badCount  = putts.count { it.quality.name == "BAD"  }
        )
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { sessionRepo.deleteSession(id) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    onSessionClick: (Long) -> Unit,
    vm            : SessionHistoryViewModel = hiltViewModel()
) {
    val state          by vm.uiState.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<SessionSummary?>(null) }
    var showTrends     by remember { mutableStateOf(false) }

    // Confirm delete dialog
    deleteCandidate?.let { cand ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor   = SurfaceMid,
            title    = { Text("Eliminar sesión", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text     = { Text("¿Eliminar esta sesión y todos sus putts? No se puede deshacer.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSession(cand.session.id); deleteCandidate = null }) {
                    Text("Eliminar", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("HISTORIAL", style = MaterialTheme.typography.headlineMedium.copy(
                    color = PuttGreen, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
                Text("${state.sessions.size} sesiones",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
            // Toggle trend charts
            if (state.tempoTrend.size >= 3 || state.headTrend.size >= 3) {
                IconButton(onClick = { showTrends = !showTrends }) {
                    Icon(
                        if (showTrends) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        "Tendencias", tint = if (showTrends) PuttGreen else TextSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = SurfaceHigh)

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PuttGreen)
            }
            state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⛳", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin sesiones todavía.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Empieza desde LIVE.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextDisabled),
                        textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(
                contentPadding      = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Trend charts (collapsible)
                if (showTrends) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            if (state.tempoTrend.size >= 3) {
                                MultiSessionChart(
                                    points   = state.tempoTrend,
                                    label    = "TEMPO A LO LARGO DEL TIEMPO",
                                    color    = MetricTempo,
                                    idealMin = 1.8f, idealMax = 2.6f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(20.dp))
                            }
                            if (state.headTrend.size >= 3) {
                                MultiSessionChart(
                                    points   = state.headTrend,
                                    label    = "HEAD STABILITY A LO LARGO DEL TIEMPO",
                                    color    = MetricHead,
                                    idealMin = 80f, idealMax = 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = SurfaceHigh) }
                }

                // Swipe hint
                item {
                    Text("← Desliza para eliminar",
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
                }

                items(state.sessions, key = { it.session.id }) { summary ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { v ->
                            if (v != SwipeToDismissBoxValue.Settled) {
                                deleteCandidate = summary; false   // show confirm dialog instead
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state             = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled)
                                    SurfaceDeep else ErrorRed.copy(0.8f),
                                tween(250), label = "del"
                            )
                            Box(
                                Modifier.fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .background(color, RoundedCornerShape(14.dp)),
                                Alignment.CenterEnd
                            ) {
                                Icon(Icons.Filled.Delete, "Eliminar",
                                    tint = Color.White, modifier = Modifier.padding(end = 20.dp))
                            }
                        }
                    ) {
                        SessionCard(
                            summary   = summary,
                            onClick   = { onSessionClick(summary.session.id) },
                            onDelete  = { deleteCandidate = summary }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val s   = summary.session
    val fmt = SimpleDateFormat("EEE dd MMM · HH:mm", Locale.getDefault())

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onClick() },
        shape    = RoundedCornerShape(14.dp),
        color    = SurfaceMid
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(fmt.format(Date(s.createdAt)),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerspectivePill(s.perspectiveType.label)
                        EnvPill(s.environmentType.label)
                    }
                }
                IconButton(onClick = onDelete, Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, "Eliminar",
                        tint = TextDisabled, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Count + quality row
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                StatPill("${summary.puttCount} putts", TextPrimary, Modifier.weight(1f))
                if (summary.goodCount > 0) StatPill("✓ ${summary.goodCount}", PuttGreen, Modifier.weight(1f))
                if (summary.badCount  > 0) StatPill("✗ ${summary.badCount}",  ErrorRed,  Modifier.weight(1f))
            }

            // Metric row (only if data available)
            val hasMetrics = summary.avgTempo != null || summary.avgHead != null || summary.avgFace != null
            if (hasMetrics) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    summary.avgTempo?.let {
                        MetricMini("TEMPO", "%.2f".format(it),
                            if (it in 1.8f..2.6f) PuttGreen else ImpactAmber, Modifier.weight(1f))
                    }
                    summary.avgHead?.let {
                        MetricMini("HEAD", "%.0f".format(it),
                            if (it >= 75f) PuttGreen else ImpactAmber, Modifier.weight(1f))
                    }
                    summary.avgFace?.let {
                        MetricMini("FACE", "%.1f°".format(it),
                            if (it in -2f..2f) PuttGreen else ImpactAmber, Modifier.weight(1f))
                    }
                    summary.avgPath?.let {
                        MetricMini("PATH", "%.1f°".format(it),
                            if (it in -3f..3f) PuttGreen else ImpactAmber, Modifier.weight(1f))
                    }
                }
            }

            if (s.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(s.notes, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.End) {
                Text("Ver LAB  ›",
                    style = MaterialTheme.typography.labelSmall.copy(color = PuttGreen))
            }
        }
    }
}

@Composable private fun PerspectivePill(label: String) =
    Surface(shape = RoundedCornerShape(6.dp), color = ConfirmBlue.copy(0.12f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = ConfirmBlue)) }

@Composable private fun EnvPill(label: String) =
    Surface(shape = RoundedCornerShape(6.dp), color = SurfaceHigh) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)) }

@Composable private fun StatPill(text: String, color: Color, modifier: Modifier) =
    Surface(modifier, RoundedCornerShape(8.dp), SurfaceDeep) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold)) }

@Composable private fun MetricMini(label: String, value: String, color: Color, modifier: Modifier) =
    Surface(modifier, RoundedCornerShape(8.dp), SurfaceDeep) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontWeight = FontWeight.Bold))
        }
    }
