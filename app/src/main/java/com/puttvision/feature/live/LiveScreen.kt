package com.puttvision.feature.live

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.*
import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.ui.theme.*
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "LiveScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveScreen(
    sessionId : Long,
    onGoToLab : () -> Unit,
    onBack    : () -> Unit,
    vm        : LiveViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { vm.init(sessionId); vm.startAudioDetection() }
    DisposableEffect(Unit) { onDispose { vm.stopAudioDetection() } }

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    Box(modifier = Modifier.fillMaxSize().background(Background)) {

        when {
            permissions.allPermissionsGranted ->
                CameraPreviewWithRecording(sessionId = sessionId, vm = vm)
            else ->
                PermissionRationale { permissions.launchMultiplePermissionRequest() }
        }

        // Skeleton overlay
        if (uiState.skeletonLandmarks.isNotEmpty()) {
            SkeletonOverlay(uiState.skeletonLandmarks, uiState.sessionState,
                Modifier.fillMaxSize())
        }

        // Debug overlay (debug builds only)
        DebugOverlay(
            uiState  = uiState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 52.dp)
        )

        // VU meter — bottom left
        VuMeterOverlay(
            level        = uiState.audioLevel,
            isArmed      = uiState.isAudioArmed,
            sessionState = uiState.sessionState,
            modifier     = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 80.dp)
        )

        // Top bar
        LiveTopBar(uiState.puttCount, onBack, onGoToLab,
            uiState.supportsHighSpeed, uiState.highSpeedEnabled,
            onToggleHighSpeed = { vm.toggleHighSpeed() },
            modifier = Modifier.align(Alignment.TopCenter))

        // State badge
        StateBadge(uiState, Modifier.align(Alignment.TopCenter).padding(top = 84.dp))

        // Address progress
        AnimatedVisibility(
            visible  = uiState.sessionState == LiveSessionState.SEARCHING_ADDRESS
                    && uiState.addressProgress > 0f,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mantenga la posición…",
                    style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.addressProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color    = PuttGreen, trackColor = SurfaceHigh
                )
            }
        }

        // Metrics flash
        AnimatedVisibility(
            visible  = uiState.sessionState == LiveSessionState.SAVED && uiState.lastMetrics != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it }, exit = slideOutVertically { it }
        ) {
            uiState.lastMetrics?.let { MetricsFlash(it) }
        }

        // Manual impact button
        AnimatedVisibility(
            visible  = uiState.sessionState == LiveSessionState.BUFFERING,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Button(onClick = { vm.manualImpact() },
                colors = ButtonDefaults.buttonColors(containerColor = ImpactAmber)) {
                Text("IMPACTO MANUAL", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CameraX binding
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreviewWithRecording(sessionId: Long, vm: LiveViewModel) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var recManager by remember { mutableStateOf<VideoRecordingManager?>(null) }

    LaunchedEffect(Unit) {
        vm.recordingCommand.collect { cmd ->
            when (cmd) {
                RecordingCommand.StartClip -> recManager?.startClip()
                RecordingCommand.StopClip  -> recManager?.stopClip()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { recManager?.release(); executor.shutdown() } }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        bindCamera(context, lifecycleOwner, previewView, executor, sessionId, vm) { mgr ->
            recManager = mgr
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun bindCamera(
    context        : Context,
    lifecycleOwner : LifecycleOwner,
    previewView    : PreviewView,
    executor       : ExecutorService,
    sessionId      : Long,
    vm             : LiveViewModel,
    onReady        : (VideoRecordingManager) -> Unit
) {
    ProcessCameraProvider.getInstance(context).addListener({
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview  = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build().also { ia -> ia.setAnalyzer(executor) { proxy -> vm.onCameraFrame(proxy) } }
        val recorder     = Recorder.Builder()
            .setExecutor(executor)
            .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD)))
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, analysis, videoCapture)
            val mgr = VideoRecordingManager(context, videoCapture, executor, sessionId)
            onReady(mgr)
            lifecycleOwner.lifecycleScope.launch {
                mgr.events.collect { event ->
                    when (event) {
                        is VideoRecordingManager.VideoEvent.Saved -> vm.onVideoSaved(event.path)
                        is VideoRecordingManager.VideoEvent.Error ->
                            Log.e(TAG, "Video error [${event.code}]: ${event.message}")
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
    }, ContextCompat.getMainExecutor(context))
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlay components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveTopBar(
    puttCount         : Int,
    onBack            : () -> Unit,
    onGoToLab         : () -> Unit,
    supportsHighSpeed : Boolean,
    highSpeedEnabled  : Boolean,
    onToggleHighSpeed : () -> Unit,
    modifier          : Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.55f))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(PuttGreen, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("$puttCount putts",
                style = MaterialTheme.typography.labelLarge.copy(color = TextPrimary))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 120fps toggle (only when device supports it)
            if (supportsHighSpeed) {
                Surface(
                    shape  = RoundedCornerShape(6.dp),
                    color  = if (highSpeedEnabled) PuttGreen.copy(alpha = 0.18f)
                             else SurfaceDeep,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    TextButton(
                        onClick  = onToggleHighSpeed,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            if (highSpeedEnabled) "120fps ●" else "120fps",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color  = if (highSpeedEnabled) PuttGreen else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            IconButton(onClick = onGoToLab) {
                Icon(Icons.Filled.VideoLibrary, null, tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun StateBadge(uiState: LiveUiState, modifier: Modifier) {
    val fg = Color(uiState.stateTint)
    Surface(modifier.padding(horizontal = 16.dp), RoundedCornerShape(20.dp), fg.copy(alpha = 0.18f)) {
        Text(uiState.stateLabel, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(color = fg, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MetricsFlash(metrics: PuttMetrics) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
        shape    = RoundedCornerShape(14.dp),
        color    = SurfaceMid.copy(alpha = 0.94f)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("ÚLTIMO PUTT",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                FlashPill("TEMPO", metrics.tempoRatio?.let { "%.2f".format(it) } ?: "—",
                    when {
                        metrics.tempoRatio == null          -> TextSecondary
                        metrics.tempoRatio!! in 1.8f..2.6f -> PuttGreen
                        else                               -> ImpactAmber
                    })
                FlashPill("HEAD",  metrics.headStability?.let { "%.0f".format(it) } ?: "—", MetricHead)
                FlashPill("FACE",  metrics.faceAngleDeg?.let  { "%.1f°".format(it) } ?: "—", MetricFace)
                FlashPill("PATH",  metrics.pathAngleDeg?.let  { "%.1f°".format(it) } ?: "—", MetricPath)
            }
        }
    }
}

@Composable
private fun FlashPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(
            color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("PuttVision necesita acceso a la\ncámara y el micrófono.",
            style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = PuttGreen)) {
            Text("Conceder permisos", color = Background)
        }
    }
}
