package com.puttvision.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.puttvision.core.data.preferences.AppSettings
import com.puttvision.ui.theme.*
import com.puttvision.vision.ColorMarkerDetector
import com.puttvision.vision.MarkerDetectionResult
import org.opencv.core.Scalar
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "HsvCalibPreview"

/**
 * A full-screen dialog that shows the live camera feed with detected HSV blobs
 * overlaid as coloured circles. Used from SettingsScreen to calibrate marker ranges.
 *
 * Green blobs = GREEN marker detections
 * Orange blobs = ORANGE marker detections
 *
 * The user adjusts sliders in SettingsScreen while watching this preview to
 * confirm the stickers are being detected reliably.
 *
 * STATUS: FUNCTIONAL — uses the same [ColorMarkerDetector] HSV pipeline.
 */
@Composable
fun HsvCalibrationPreviewDialog(
    settings : AppSettings,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = com.puttvision.ui.theme.Background
        ) {
            Box(Modifier.fillMaxSize()) {
                // Live camera with blob overlay
                CalibPreviewCamera(settings = settings)

                // Top bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    color    = Background.copy(alpha = 0.7f)
                ) {
                    Row(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, "Cerrar", tint = TextPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Vista de calibración",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = TextPrimary, fontWeight = FontWeight.Bold))
                            Text("🟢 Toe  🟠 Heel",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        }
                    }
                }

                // Bottom hint
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color    = Background.copy(alpha = 0.75f)
                ) {
                    Text(
                        "Los círculos deben aparecer sobre las pegatinas del putter.\n" +
                        "Vuelve a Ajustes para modificar los rangos HSV.",
                        modifier = Modifier.padding(16.dp),
                        style    = MaterialTheme.typography.bodyMedium.copy(
                            color      = TextSecondary,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera composable with overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalibPreviewCamera(settings: AppSettings) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        ColorMarkerDetector().also { it.applySettings(settings) }
    }

    // Detected blob positions for overlay (normalised 0..1)
    var greenX  by remember { mutableStateOf(-1f) }
    var greenY  by remember { mutableStateOf(-1f) }
    var orangeX by remember { mutableStateOf(-1f) }
    var orangeY by remember { mutableStateOf(-1f) }
    var imgW    by remember { mutableIntStateOf(1) }
    var imgH    by remember { mutableIntStateOf(1) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            detector.release()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(settings) {
        detector.applySettings(settings)
    }

    LaunchedEffect(Unit) {
        ProcessCameraProvider.getInstance(context).addListener({
            val provider = ProcessCameraProvider.getInstance(context).get()
            val preview  = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { ia ->
                    ia.setAnalyzer(executor) { proxy ->
                        imgW = proxy.width; imgH = proxy.height
                        val bmp = proxy.yuvToBitmapSimple()
                        if (bmp != null) {
                            try {
                                when (val r = detector.detect(bmp)) {
                                    is MarkerDetectionResult.Found -> {
                                        greenX  = (r.greenCenter.x  / imgW).toFloat()
                                        greenY  = (r.greenCenter.y  / imgH).toFloat()
                                        orangeX = (r.orangeCenter.x / imgW).toFloat()
                                        orangeY = (r.orangeCenter.y / imgH).toFloat()
                                    }
                                    is MarkerDetectionResult.NotFound -> {
                                        if (!r.greenFound)  { greenX  = -1f; greenY  = -1f }
                                        if (!r.orangeFound) { orangeX = -1f; orangeY = -1f }
                                    }
                                }
                            } catch (e: Exception) { Log.w(TAG, e.message ?: "") }
                        }
                        proxy.close()
                    }
                }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { Log.e(TAG, "Bind failed", e) }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Blob dot overlay via Canvas
        BlobOverlay(
            greenX  = greenX,  greenY  = greenY,
            orangeX = orangeX, orangeY = orangeY,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BlobOverlay(
    greenX : Float, greenY : Float,
    orangeX: Float, orangeY: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val r = 18.dp.toPx()

        if (greenX >= 0f) {
            drawCircle(androidx.compose.ui.graphics.Color(0xFF00E676), r,
                androidx.compose.ui.geometry.Offset(greenX * w, greenY * h))
            drawCircle(androidx.compose.ui.graphics.Color.White, r * 0.35f,
                androidx.compose.ui.geometry.Offset(greenX * w, greenY * h))
        }
        if (orangeX >= 0f) {
            drawCircle(androidx.compose.ui.graphics.Color(0xFFFFB300), r,
                androidx.compose.ui.geometry.Offset(orangeX * w, orangeY * h))
            drawCircle(androidx.compose.ui.graphics.Color.White, r * 0.35f,
                androidx.compose.ui.geometry.Offset(orangeX * w, orangeY * h))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Minimal YUV → Bitmap for the preview (no frame buffer, just analysis)
// ─────────────────────────────────────────────────────────────────────────────

private fun ImageProxy.yuvToBitmapSimple(): Bitmap? = runCatching {
    val yBuf = planes[0].buffer; val uBuf = planes[1].buffer; val vBuf = planes[2].buffer
    val ySize = yBuf.remaining(); val uSize = uBuf.remaining(); val vSize = vBuf.remaining()
    val nv21  = ByteArray(ySize + uSize + vSize)
    yBuf.get(nv21, 0, ySize); vBuf.get(nv21, ySize, vSize); uBuf.get(nv21, ySize + vSize, uSize)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 70, out)
    BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}.getOrNull()
