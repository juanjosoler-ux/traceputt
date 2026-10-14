package com.puttvision.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puttvision.audio.AdaptiveAudioThreshold
import com.puttvision.audio.AudioImpactDetector
import com.puttvision.core.data.preferences.AppSettings
import com.puttvision.core.data.preferences.SettingsDataStore
import com.puttvision.vision.ColorMarkerDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val audioThreshold      : Float   = 3_500f,
    val greenHMin           : Float   = 40f,
    val greenHMax           : Float   = 80f,
    val greenSMin           : Float   = 150f,
    val greenVMin           : Float   = 100f,
    val orangeHMin          : Float   = 8f,
    val orangeHMax          : Float   = 20f,
    val orangeSMin          : Float   = 200f,
    val orangeVMin          : Float   = 150f,
    /** Whether the adaptive threshold has been calibrated this session */
    val isAdaptiveCalibrated: Boolean = false,
    /** The calibrated threshold value (displayed in UI) */
    val calibratedThreshold : Float?  = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore     : SettingsDataStore,
    private val markerDetector    : ColorMarkerDetector,
    private val audioDetector     : AudioImpactDetector,
    private val adaptiveThreshold : AdaptiveAudioThreshold
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var _current = AppSettings()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _current   = s
                _uiState.update { it.copy(
                    audioThreshold = s.audioThreshold,
                    greenHMin = s.greenHMin, greenHMax = s.greenHMax,
                    greenSMin = s.greenSMin, greenVMin = s.greenVMin,
                    orangeHMin = s.orangeHMin, orangeHMax = s.orangeHMax,
                    orangeSMin = s.orangeSMin, orangeVMin = s.orangeVMin
                )}
            }
        }
        // Poll adaptive threshold calibration status
        viewModelScope.launch {
            while (true) {
                val calibrated = adaptiveThreshold.isCalibrated
                val value      = if (calibrated) adaptiveThreshold.computedThreshold else null
                _uiState.update { it.copy(
                    isAdaptiveCalibrated = calibrated,
                    calibratedThreshold  = value
                )}
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun currentSettings(): AppSettings = _current

    fun setAudioThreshold(v: Float) = save { it.copy(audioThreshold = v) }
    fun setGreenHMin(v: Float)      = save { it.copy(greenHMin  = v) }
    fun setGreenHMax(v: Float)      = save { it.copy(greenHMax  = v) }
    fun setGreenSMin(v: Float)      = save { it.copy(greenSMin  = v) }
    fun setGreenVMin(v: Float)      = save { it.copy(greenVMin  = v) }
    fun setOrangeHMin(v: Float)     = save { it.copy(orangeHMin = v) }
    fun setOrangeHMax(v: Float)     = save { it.copy(orangeHMax = v) }
    fun setOrangeSMin(v: Float)     = save { it.copy(orangeSMin = v) }
    fun setOrangeVMin(v: Float)     = save { it.copy(orangeVMin = v) }
    fun resetHsvDefaults()          = save { it.copy(
        greenHMin  = 40f, greenHMax  = 80f,  greenSMin  = 150f, greenVMin  = 100f,
        orangeHMin = 8f,  orangeHMax = 20f,  orangeSMin = 200f, orangeVMin = 150f
    )}

    private fun save(mutate: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val next = mutate(_current)
            settingsStore.save(next)
            markerDetector.applySettings(next)
            audioDetector.impactThreshold = next.audioThreshold
        }
    }

    private fun AppSettings.toUiState() = SettingsUiState(
        audioThreshold = audioThreshold,
        greenHMin = greenHMin, greenHMax = greenHMax,
        greenSMin = greenSMin, greenVMin = greenVMin,
        orangeHMin = orangeHMin, orangeHMax = orangeHMax,
        orangeSMin = orangeSMin, orangeVMin = orangeVMin
    )
}
