package com.puttvision.feature.lab

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttQuality
import com.puttvision.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    sessionId  : Long?,
    onPuttClick: ((Long) -> Unit)? = null,
    vm         : LabViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }
    val state    by vm.uiState.collectAsStateWithLifecycle()
    val focusMgr  = LocalFocusManager.current
    var editNotes by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("LAB", style = MaterialTheme.typography.headlineMedium.copy(
                    color = PuttGreen, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
                state.session?.let { s ->
                    Text(
                        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
                            .format(Date(s.createdAt)) + " · " + s.perspectiveType.label,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            }
            // Notes edit toggle
            if (state.session != null) {
                IconButton(onClick = { editNotes = !editNotes }) {
                    Icon(
                        if (editNotes) Icons.Filled.Done else Icons.Filled.Edit,
                        contentDescription = "Notas",
                        tint = if (editNotes) PuttGreen else TextSecondary
                    )
                }
            }
            Text("${state.putts.size} putts",
                style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
        }

        // Notes saved snackbar
        if (state.showNotesSaved) {
            Surface(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(8.dp),
                color     = PuttGreen.copy(alpha = 0.15f)
            ) {
                Text("Notas guardadas ✓",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(color = PuttGreen))
            }
            Spacer(Modifier.height(6.dp))
        }

        // Notes editor
        if (editNotes) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value         = state.notesDraft,
                    onValueChange = { vm.updateNotesDraft(it) },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("Notas de sesión") },
                    placeholder   = { Text("Condiciones, sensaciones, objetivos…",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextDisabled)) },
                    maxLines      = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusMgr.clearFocus()
                        vm.saveNotes()
                        editNotes = false
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PuttGreen,
                        unfocusedBorderColor = SurfaceHigh,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = PuttGreen
                    )
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.End) {
                    TextButton(onClick = {
                        focusMgr.clearFocus()
                        vm.saveNotes()
                        editNotes = false
                    }) {
                        Text("Guardar", color = PuttGreen)
                    }
                }
            }
        }

        HorizontalDivider(color = SurfaceHigh)

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PuttGreen)
            }
            state.putts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Sin putts registrados.\nInicia una sesión LIVE.",
                    style     = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            else -> {
                // Swipe hint (shown once)
                Text(
                    "← Desliza para eliminar",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled)
                )

                LazyColumn(
                    contentPadding      = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = state.putts,
                        key   = { _, item -> item.record.id }
                    ) { idx, item ->
                        // Swipe-to-delete
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd ||
                                    value == SwipeToDismissBoxValue.EndToStart) {
                                    vm.deletePutt(item.record.id)
                                    true
                                } else false
                            },
                            positionalThreshold = { it * 0.35f }
                        )

                        SwipeToDismissBox(
                            state            = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue   = if (dismissState.targetValue ==
                                        SwipeToDismissBoxValue.Settled) SurfaceDeep else ErrorRed.copy(alpha = 0.8f),
                                    animationSpec = tween(250),
                                    label         = "deleteColor"
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(color),
                                    Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Filled.Delete, "Eliminar",
                                        tint     = Color.White,
                                        modifier = Modifier.padding(end = 20.dp))
                                }
                            },
                            modifier = Modifier.padding(horizontal = 0.dp)
                        ) {
                            PuttCard(
                                index      = idx + 1,
                                item       = item,
                                onClick    = { onPuttClick?.invoke(item.record.id) },
                                onGood     = { vm.tagQuality(item.record.id, PuttQuality.GOOD) },
                                onBad      = { vm.tagQuality(item.record.id, PuttQuality.BAD) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Putt card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PuttCard(
    index  : Int,
    item   : PuttWithMetrics,
    onClick: () -> Unit,
    onGood : () -> Unit,
    onBad  : () -> Unit
) {
    val record  = item.record
    val metrics = item.metrics
    var showVideo by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape    = RoundedCornerShape(14.dp),
        color    = SurfaceMid
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("#$index", style = MaterialTheme.typography.headlineSmall.copy(
                    color = TextPrimary, fontWeight = FontWeight.Bold))
                Text(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.createdAt)),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(record.quality)
                    Spacer(Modifier.width(6.dp))
                    Text("›", style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (record.videoPath != null && File(record.videoPath).exists()) {
                if (showVideo) {
                    InlineVideoPlayer(
                        videoPath = record.videoPath,
                        modifier  = Modifier.fillMaxWidth().height(190.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    TextButton(onClick = { showVideo = false }) {
                        Text("Cerrar", color = TextSecondary)
                    }
                } else {
                    OutlinedButton(
                        onClick  = { showVideo = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHigh)
                    ) { Text("▶  Reproducir clip", color = PuttGreen) }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (metrics != null) {
                MetricsGrid(metrics)
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                IconButton(onClick = onBad,  Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ThumbDown, "Malo",
                        tint = if (record.quality == PuttQuality.BAD) ErrorRed else TextDisabled)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onGood, Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ThumbUp, "Bueno",
                        tint = if (record.quality == PuttQuality.GOOD) PuttGreen else TextDisabled)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video player
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InlineVideoPlayer(videoPath: String, modifier: Modifier = Modifier) {
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

// ─────────────────────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(metrics: PuttMetrics) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        MetricCell("TEMPO", metrics.tempoRatio?.let { "%.2f".format(it) } ?: "—",
            metrics.tempoRatio?.let { if (it in 1.8f..2.6f) "✓" else "!" }, MetricTempo, Modifier.weight(1f))
        MetricCell("HEAD",  metrics.headStability?.let { "%.0f".format(it) } ?: "—",
            null, MetricHead, Modifier.weight(1f))
        MetricCell("FACE",  metrics.faceAngleDeg?.let { "%.1f°".format(it) } ?: "—",
            null, MetricFace, Modifier.weight(1f))
        MetricCell("PATH",  metrics.pathAngleDeg?.let { "%.1f°".format(it) } ?: "—",
            null, MetricPath, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(label: String, value: String, sub: String?, color: Color, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(8.dp), SurfaceDeep) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(
                color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp))
            sub?.let {
                Text(it, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.7f)))
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: PuttQuality) {
    val (text, color) = when (quality) {
        PuttQuality.GOOD     -> "BUENO" to PuttGreen
        PuttQuality.BAD      -> "MALO"  to ErrorRed
        PuttQuality.UNTAGGED -> return
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = color, fontWeight = FontWeight.Bold))
    }
}
