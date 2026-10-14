package com.puttvision.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puttvision.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val emoji   : String,
    val title   : String,
    val body    : String,
    val tipLabel: String = "",
    val tip     : String = ""
)

private val PAGES = listOf(
    OnboardingPage(
        emoji    = "⛳",
        title    = "Bienvenido a PuttVision",
        body     = "Análisis de putting con solo tu smartphone. Sin sensores externos.",
        tipLabel = "Compatibilidad",
        tip      = "Funciona en cualquier Android 8+ con cámara trasera."
    ),
    OnboardingPage(
        emoji    = "📐",
        title    = "Elige perspectiva",
        body     = "Coloca el teléfono según la perspectiva que quieres analizar:\n\n" +
                   "• Detrás → path y salida de bola\n" +
                   "• Frente → face angle y cabeza\n" +
                   "• Lateral → tempo y dinámica",
        tipLabel = "Soporte recomendado",
        tip      = "Usa un trípode o apoya el teléfono a unos 2m de distancia y altura de cadera."
    ),
    OnboardingPage(
        emoji    = "🎙️",
        title    = "Detección automática",
        body     = "El micrófono detecta el impacto palo-bola de forma automática.\n\n" +
                   "El análisis se ejecuta solo al oír el golpe.",
        tipLabel = "Si no detecta el impacto",
        tip      = "Sube el volumen del entorno o baja el umbral en Ajustes."
    ),
    OnboardingPage(
        emoji    = "🟢🟠",
        title    = "Marcadores de color (opcional)",
        body     = "Para medir face angle y path, pega pegatinas fluorescentes en el putter:\n\n" +
                   "• Verde → toe (extremo)\n" +
                   "• Naranja → heel (empuñadura)",
        tipLabel = "Calibración",
        tip      = "Ajusta los rangos HSV en Ajustes si la detección falla bajo tu iluminación."
    ),
    OnboardingPage(
        emoji    = "🏌️",
        title    = "¿Listo para empezar?",
        body     = "Dirígete a la pantalla LIVE, elige tu perspectiva y empieza a putar.\n\n" +
                   "El sistema detectará cuando estás en address y disparará automáticamente.",
        tipLabel = "",
        tip      = ""
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope      = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        // ── Pager ─────────────────────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            PageContent(PAGES[page])
        }

        // ── Dots indicator ────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            repeat(PAGES.size) { idx ->
                val selected = idx == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(if (selected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (selected) PuttGreen else SurfaceHigh)
                )
            }
        }

        // ── Navigation buttons ────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }) {
                    Text("Atrás", color = TextSecondary)
                }
            } else {
                Spacer(Modifier.width(80.dp))
            }

            val isLast = pagerState.currentPage == PAGES.lastIndex
            Button(
                onClick  = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.height(52.dp).width(160.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PuttGreen, contentColor = Background)
            ) {
                Text(
                    if (isLast) "EMPEZAR" else "Siguiente",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement  = Arrangement.Center,
        horizontalAlignment  = Alignment.CenterHorizontally
    ) {
        Text(page.emoji, fontSize = 56.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            page.title,
            style     = MaterialTheme.typography.headlineSmall.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.body,
            style     = MaterialTheme.typography.bodyLarge.copy(
                color      = TextSecondary,
                lineHeight = 26.sp
            ),
            textAlign = TextAlign.Center
        )
        if (page.tip.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                color    = PuttGreen.copy(alpha = 0.08f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (page.tipLabel.isNotEmpty()) {
                        Text(
                            page.tipLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = PuttGreen, letterSpacing = 1.sp
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(page.tip,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = TextPrimary,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }
    }
}
