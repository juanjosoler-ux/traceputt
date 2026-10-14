package com.puttvision.feature.lab

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttQuality
import com.puttvision.core.domain.model.PuttRecord
import com.puttvision.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

data class PuttDetailUiState(
    val record     : PuttRecord?   = null,
    val metrics    : PuttMetrics?  = null,
    val sessionAvg : SessionAverage? = null,
    val isLoading  : Boolean       = true
)

@HiltViewModel
class PuttDetailViewModel @Inject constructor(
    private val puttRepo: PuttRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuttDetailUiState())
    val uiState: StateFlow<PuttDetailUiState> = _uiState.asStateFlow()

    fun load(puttId: Long) {
        viewModelScope.launch {
            val record  = puttRepo.getRecord(puttId)
            val metrics = puttRepo.getMetrics(puttId)
            val avg     = record?.let { buildSessionAvg(it.sessionId) }
            _uiState.update { it.copy(record = record, metrics = metrics, sessionAvg = avg, isLoading = false) }
        }
    }

    private suspend fun buildSessionAvg(sessionId: Long) = SessionAverage(
        tempoRatio    = puttRepo.avgTempoRatio(sessionId),
        headStability = puttRepo.avgHeadStability(sessionId),
        faceAngle     = puttRepo.avgFaceAngle(sessionId),
        pathAngle     = puttRepo.avgPathAngle(sessionId)
    )

    fun setQuality(quality: PuttQuality) {
        val id = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            puttRepo.updateQuality(id, quality)
            val updated = puttRepo.getRecord(id)
            _uiState.update { it.copy(record = updated) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PuttDetailScreen(
    puttId: Long,
    onBack: () -> Unit,
    vm    : PuttDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(puttId) { vm.load(puttId) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Atrás", tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("PUTT #$puttId", style = MaterialTheme.typography.headlineSmall.copy(
                    color = TextPrimary, fontWeight = FontWeight.Black))
                state.record?.let { r ->
                    Text(
                        SimpleDateFormat("dd/MM/yyyy · HH:mm:ss", Locale.getDefault())
                            .format(Date(r.createdAt)),
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }
            state.record?.let { r ->
                IconButton(onClick = { vm.setQuality(PuttQuality.BAD) }) {
                    Icon(Icons.Filled.ThumbDown, "Malo",
                        tint = if (r.quality == PuttQuality.BAD) ErrorRed else TextDisabled)
                }
                IconButton(onClick = { vm.setQuality(PuttQuality.GOOD) }) {
                    Icon(Icons.Filled.ThumbUp, "Bueno",
                        tint = if (r.quality == PuttQuality.GOOD) PuttGreen else TextDisabled)
                }
            }
        }
        HorizontalDivider(color = SurfaceHigh)

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PuttGreen)
            }
            return@Column
        }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Video player
            val videoPath = state.record?.videoPath
            if (videoPath != null && File(videoPath).exists()) {
                DetailVideoPlayer(
                    videoPath = videoPath,
                    modifier  = Modifier.fillMaxWidth().height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(16.dp))
            }

            // Confidence badge
            state.metrics?.let { m ->
                Surface(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(10.dp),
                    when {
                        m.overallConf >= 0.8f -> PuttGreen.copy(0.12f)
                        m.overallConf >= 0.5f -> ImpactAmber.copy(0.12f)
                        else                  -> ErrorRed.copy(0.10f)
                    }
                ) {
                    Text(
                        "Confianza de detección: ${"%.0f%%".format(m.overallConf * 100)}",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = when {
                                m.overallConf >= 0.8f -> PuttGreen
                                m.overallConf >= 0.5f -> ImpactAmber
                                else                  -> ErrorRed
                            },
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Metrics detail cards
            state.metrics?.let { m ->
                MetricDetailCard("TEMPO", MetricTempo,
                    m.tempoRatio?.let { "%.2f".format(it) } ?: "—",
                    m.tempoRatio?.let { tempoLabel(it) } ?: "Sin datos",
                    listOf(
                        "Backswing"       to (m.backswingMs?.let { "${it}ms" } ?: "—"),
                        "Follow-through"  to (m.followMs?.let { "${it}ms" } ?: "—"),
                        "Ratio ideal"     to "2.0 – 2.6"
                    )
                )
                Spacer(Modifier.height(10.dp))

                MetricDetailCard("HEAD STABILITY", MetricHead,
                    m.headStability?.let { "%.0f".format(it) } ?: "—",
                    m.headStability?.let { headLabel(it) } ?: "Sin datos",
                    listOf(
                        "Desplazamiento máx." to (m.headMovePx?.let { "%.1fpx".format(it) } ?: "—"),
                        "Ideal"               to "> 85 / 100"
                    )
                )
                Spacer(Modifier.height(10.dp))

                MetricDetailCard("FACE ANGLE", MetricFace,
                    m.faceAngleDeg?.let { "%.1f°".format(it) } ?: "—",
                    m.faceAngleDeg?.let { faceLabel(it) } ?: "Calibrar marcadores HSV",
                    listOf("Perspectiva recomendada" to "Frente / Detrás", "Ideal" to "0.0°")
                )
                Spacer(Modifier.height(10.dp))

                MetricDetailCard("PATH", MetricPath,
                    m.pathAngleDeg?.let { "%.1f°".format(it) } ?: "—",
                    m.pathAngleDeg?.let { pathLabel(it) } ?: "Necesita marcadores",
                    listOf("+ = Outside-in" to "− = Inside-out", "Ideal" to "0.0°")
                )
                Spacer(Modifier.height(16.dp))

                // Comparison vs session average
                state.sessionAvg?.let { avg ->
                    if (avg.tempoRatio != null || avg.headStability != null) {
                        PuttComparisonCard(current = m, avg = avg)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } ?: run {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
                    Text("No hay métricas calculadas para este putt.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailVideoPlayer(videoPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player  = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player   = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun MetricDetailCard(
    title: String, color: Color,
    primary: String, subtitle: String,
    rows: List<Pair<String, String>>
) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(title, style = MaterialTheme.typography.labelLarge.copy(
                        color = TextSecondary, letterSpacing = 1.5.sp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = color))
                }
                Text(primary, style = MaterialTheme.typography.displayLarge.copy(
                    color = color, fontWeight = FontWeight.Bold, fontSize = 40.sp))
            }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SurfaceHigh)
                Spacer(Modifier.height(8.dp))
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text(label,  style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Text(value,  style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
                    }
                }
            }
        }
    }
}

private fun tempoLabel(r: Float) = when {
    r < 1.4f -> "Backswing demasiado rápido"
    r < 1.8f -> "Ligeramente acelerado"
    r < 2.6f -> "Tempo correcto ✓"
    r < 3.2f -> "Ligeramente lento"
    else     -> "Backswing demasiado lento"
}
private fun headLabel(s: Float) = when {
    s >= 90f -> "Excelente estabilidad ✓"
    s >= 75f -> "Buena estabilidad"
    s >= 55f -> "Movimiento moderado"
    else     -> "Cabeza muy móvil"
}
private fun faceLabel(d: Float) = when {
    d > 2f  -> "Cara abierta"
    d < -2f -> "Cara cerrada"
    else    -> "Cara cuadrada ✓"
}
private fun pathLabel(d: Float) = when {
    d > 3f  -> "Outside-in (corte)"
    d < -3f -> "Inside-out (gancho)"
    else    -> "Path recto ✓"
}
