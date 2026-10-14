package com.puttvision.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puttvision.core.data.preferences.UserPreferences
import com.puttvision.core.data.preferences.UserPreferencesDataStore
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.EnvironmentType
import com.puttvision.core.domain.model.PerspectiveType
import com.puttvision.core.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeStats(
    val totalSessions : Int    = 0,
    val totalPutts    : Int    = 0,
    val lastTempo     : Float? = null,
    val lastHeadScore : Float? = null
)

data class HomeUiState(
    val environment      : EnvironmentType = EnvironmentType.HOME,
    val perspective      : PerspectiveType = PerspectiveType.BEHIND,
    val recentSessions   : List<Session>   = emptyList(),
    val stats            : HomeStats       = HomeStats(),
    val isCreating       : Boolean         = false,
    val createdSessionId : Long?           = null,
    val error            : String?         = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepo : SessionRepository,
    private val puttRepo    : PuttRepository,
    private val prefsStore  : UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Restore preferences
        viewModelScope.launch {
            prefsStore.preferences.first().let { prefs ->
                _uiState.update { it.copy(environment = prefs.environment, perspective = prefs.perspective) }
            }
        }
        // Observe sessions + compute summary stats
        viewModelScope.launch {
            sessionRepo.observeAll().collect { sessions ->
                _uiState.update { it.copy(recentSessions = sessions.take(5)) }
                val totalSessions = sessions.size
                var totalPutts = 0
                var lastTempo : Float? = null
                var lastHead  : Float? = null
                sessions.firstOrNull()?.let { latest ->
                    val putts = puttRepo.getBySession(latest.id)
                    totalPutts = sessions.sumOf { s ->
                        runCatching { puttRepo.getBySession(s.id).size }.getOrDefault(0)
                    }
                    lastTempo = puttRepo.avgTempoRatio(latest.id)
                    lastHead  = puttRepo.avgHeadStability(latest.id)
                }
                _uiState.update {
                    it.copy(stats = HomeStats(
                        totalSessions = totalSessions,
                        totalPutts    = totalPutts,
                        lastTempo     = lastTempo,
                        lastHeadScore = lastHead
                    ))
                }
            }
        }
    }

    fun selectEnvironment(env: EnvironmentType) {
        _uiState.update { it.copy(environment = env) }
        viewModelScope.launch { prefsStore.save(UserPreferences(env, _uiState.value.perspective)) }
    }

    fun selectPerspective(p: PerspectiveType) {
        _uiState.update { it.copy(perspective = p) }
        viewModelScope.launch { prefsStore.save(UserPreferences(_uiState.value.environment, p)) }
    }

    fun startSession() {
        val s = _uiState.value
        _uiState.update { it.copy(isCreating = true, createdSessionId = null, error = null) }
        viewModelScope.launch {
            runCatching {
                sessionRepo.createSession(Session(environmentType = s.environment, perspectiveType = s.perspective))
            }.onSuccess { id ->
                _uiState.update { it.copy(isCreating = false, createdSessionId = id) }
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, error = e.message) }
            }
        }
    }

    fun onSessionNavigated() = _uiState.update { it.copy(createdSessionId = null) }
}
