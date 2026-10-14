package com.puttvision.feature.settings

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.puttvision.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm    : SettingsViewModel = hiltViewModel()
) {
    val state        by vm.uiState.collectAsStateWithLifecycle()
    val cameraPerms   = rememberPermissionState(Manifest.permission.CAMERA)
    var showCalib    by remember { mutableStateOf(false) }

    if (showCalib && cameraPerms.status.isGranted) {
        HsvCalibrationPreviewDialog(
            settings  = vm.currentSettings(),
            onDismiss = { showCalib = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Atrás", tint = TextPrimary)
            }
            Text("AJUSTES", style = MaterialTheme.typography.headlineSmall.copy(
                color = TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
        }
        HorizontalDivider(color = SurfaceHigh)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Audio impact ──────────────────────────────────────────────────
            SectionHeader("DETECCIÓN DE IMPACTO — AUDIO")
            Spacer(Modifier.height(12.dp))

            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Umbral manual",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                            Text("RMS ${state.audioThreshold.toInt()}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = PuttGreen, fontWeight = FontWeight.Bold))
                        }
                        Text("1000 – 8000",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value         = state.audioThreshold,
                        onValueChange = { vm.setAudioThreshold(it) },
                        valueRange    = 1000f..8000f,
                        steps         = 69,
                        colors        = SliderDefaults.colors(
                            thumbColor        = PuttGreen,
                            activeTrackColor  = PuttGreen,
                            inactiveTrackColor= SurfaceHigh
                        )
                    )
                    Text("↑ Sube para reducir falsos impactos  ·  ↓ Baja si no detecta el golpe",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, lineHeight = 18.sp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Adaptive threshold status card
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), SurfaceDeep) {
                Row(Modifier.padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(
                            "Calibración automática",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )
                        Text(
                            if (state.isAdaptiveCalibrated)
                                "Activa — umbral calculado desde ruido ambiente"
                            else
                                "Se activa al inicio de cada sesión LIVE (~2 seg)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (state.isAdaptiveCalibrated) PuttGreen else TextSecondary,
                                lineHeight = 17.sp
                            )
                        )
                    }
                    if (state.isAdaptiveCalibrated && state.calibratedThreshold != null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = PuttGreen.copy(alpha = 0.15f)) {
                            Text(
                                "RMS ${state.calibratedThreshold!!.toInt()}",
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = PuttGreen, fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        Box(
                            Modifier.size(10.dp).background(
                                if (state.isAdaptiveCalibrated) PuttGreen else TextDisabled,
                                androidx.compose.foundation.shape.CircleShape
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Color markers ─────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SectionHeader("MARCADORES DE COLOR — PUTTER")
                Button(
                    onClick  = {
                        if (cameraPerms.status.isGranted) showCalib = true
                        else cameraPerms.launchPermissionRequest()
                    },
                    modifier = Modifier.height(36.dp),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = SurfaceHigh)
                ) {
                    Icon(imageVector = Icons.Filled.Videocam, contentDescription = null, tint = PuttGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Vista previa", style = MaterialTheme.typography.labelSmall.copy(color = PuttGreen))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Verde en el toe, Naranja en el heel. Ajusta los rangos HSV hasta " +
                "que los círculos aparezcan sobre las pegatinas en la vista previa.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
            )
            Spacer(Modifier.height(14.dp))

            HsvRangeCard(
                title  = "Marcador VERDE (Toe)",  color  = MetricTempo,
                hMin = state.greenHMin,  hMax = state.greenHMax,
                sMin = state.greenSMin,  vMin = state.greenVMin,
                onHMin = { vm.setGreenHMin(it) }, onHMax = { vm.setGreenHMax(it) },
                onSMin = { vm.setGreenSMin(it) }, onVMin = { vm.setGreenVMin(it) }
            )
            Spacer(Modifier.height(12.dp))
            HsvRangeCard(
                title  = "Marcador NARANJA (Heel)", color  = MetricPath,
                hMin = state.orangeHMin, hMax = state.orangeHMax,
                sMin = state.orangeSMin, vMin = state.orangeVMin,
                onHMin = { vm.setOrangeHMin(it) }, onHMax = { vm.setOrangeHMax(it) },
                onSMin = { vm.setOrangeSMin(it) }, onVMin = { vm.setOrangeVMin(it) }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick  = { vm.resetHsvDefaults() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                border   = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHigh)
            ) {
                Text("Restaurar valores por defecto",
                    style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
            }

            Spacer(Modifier.height(28.dp))

            // ── App info ──────────────────────────────────────────────────────
            SectionHeader("ACERCA DE")
            Spacer(Modifier.height(10.dp))
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Versión",   "0.1.0 (build 6)")
                    InfoRow("Cámara",    "CameraX 1.3.2")
                    InfoRow("Visión",    "ML Kit Pose + OpenCV 4.9")
                    InfoRow("Audio",     "AudioRecord 44100 Hz PCM16 + Adaptive")
                    InfoRow("DB",        "Room 2.6.1")
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary, letterSpacing = 2.sp))
}

@Composable
private fun HsvRangeCard(
    title: String, color: androidx.compose.ui.graphics.Color,
    hMin: Float, hMax: Float, sMin: Float, vMin: Float,
    onHMin: (Float) -> Unit, onHMax: (Float) -> Unit,
    onSMin: (Float) -> Unit, onVMin: (Float) -> Unit
) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), SurfaceMid) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            HsvSlider("H mín", hMin, 0f, 179f, onHMin, color)
            HsvSlider("H máx", hMax, 0f, 179f, onHMax, color)
            HsvSlider("S mín", sMin, 0f, 255f, onSMin, color)
            HsvSlider("V mín", vMin, 0f, 255f, onVMin, color)
        }
    }
}

@Composable
private fun HsvSlider(label: String, value: Float, min: Float, max: Float,
    onChange: (Float) -> Unit, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall.copy(color = color))
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = min..max,
            modifier = Modifier.height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = color, activeTrackColor = color.copy(alpha = 0.7f), inactiveTrackColor = SurfaceHigh)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
    }
}
