package com.puttvision.feature.live

import com.puttvision.core.domain.model.LiveSessionState
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.Session
import com.puttvision.vision.AddressResult

data class LiveUiState(
    val session              : Session?            = null,
    val sessionState         : LiveSessionState    = LiveSessionState.IDLE,
    val puttCount            : Int                 = 0,
    val addressResult        : AddressResult?      = null,
    val addressProgress      : Float               = 0f,
    val lastMetrics          : PuttMetrics?        = null,
    val errorMessage         : String?             = null,
    val isAudioArmed         : Boolean             = false,
    /** 0..1 normalised mic RMS for VU meter */
    val audioLevel           : Float               = 0f,
    /** True once adaptive calibration is complete (~2s after session start) */
    val isAudioCalibrated    : Boolean             = false,
    /** The effective impact threshold currently in use (adaptive or manual) */
    val effectiveThreshold   : Float               = 3_500f,
    /** Device supports 120fps capture */
    val highSpeedEnabled    : Boolean             = false,
    val supportsHighSpeed    : Boolean             = false,
    val skeletonLandmarks    : List<NormLandmark>  = emptyList()
) {
    val stateLabel: String get() = when (sessionState) {
        LiveSessionState.IDLE              -> "Listo"
        LiveSessionState.SEARCHING_ADDRESS -> "Buscando address…"
        LiveSessionState.READY             -> "● En posición"
        LiveSessionState.BUFFERING         -> "⬤ Grabando"
        LiveSessionState.IMPACT_DETECTED   -> "IMPACTO"
        LiveSessionState.ANALYZING         -> "Analizando…"
        LiveSessionState.SAVED             -> "Guardado ✓"
        LiveSessionState.ERROR             -> "Error"
    }

    val stateTint: Long get() = when (sessionState) {
        LiveSessionState.IDLE              -> 0xFF888888L
        LiveSessionState.SEARCHING_ADDRESS -> 0xFFFFB300L
        LiveSessionState.READY             -> 0xFF00E676L
        LiveSessionState.BUFFERING         -> 0xFFFF6D00L
        LiveSessionState.IMPACT_DETECTED   -> 0xFFFFFFFFL
        LiveSessionState.ANALYZING         -> 0xFF40C4FFL
        LiveSessionState.SAVED             -> 0xFF00E676L
        LiveSessionState.ERROR             -> 0xFFFF1744L
    }
}

data class NormLandmark(val x: Float, val y: Float, val confidence: Float)
