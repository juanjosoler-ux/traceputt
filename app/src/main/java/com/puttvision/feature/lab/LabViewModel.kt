package com.puttvision.feature.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttQuality
import com.puttvision.core.domain.model.PuttRecord
import com.puttvision.core.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PuttWithMetrics(
    val record : PuttRecord,
    val metrics: PuttMetrics?
)

data class LabUiState(
    val session      : Session?              = null,
    val putts        : List<PuttWithMetrics> = emptyList(),
    val isLoading    : Boolean               = true,
    val error        : String?               = null,
    val notesDraft   : String                = "",
    val showNotesSaved: Boolean              = false
)

@HiltViewModel
class LabViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val puttRepo   : PuttRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LabUiState())
    val uiState: StateFlow<LabUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load(sessionId: Long?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val targetId = sessionId ?: sessionRepo.getLatest()?.id
                if (targetId == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                val session = sessionRepo.getById(targetId)
                _uiState.update { it.copy(
                    session    = session,
                    notesDraft = session?.notes ?: ""
                )}

                puttRepo.observeBySession(targetId)
                    .map { records -> records.map { r -> PuttWithMetrics(r, puttRepo.getMetrics(r.id)) } }
                    .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                    .collect { withMetrics ->
                        _uiState.update { it.copy(putts = withMetrics, isLoading = false) }
                    }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun tagQuality(puttId: Long, quality: PuttQuality) {
        viewModelScope.launch { puttRepo.updateQuality(puttId, quality) }
    }

    fun deletePutt(puttId: Long) {
        viewModelScope.launch { puttRepo.deletePutt(puttId) }
    }

    fun updateNotesDraft(text: String) {
        _uiState.update { it.copy(notesDraft = text) }
    }

    fun saveNotes() {
        val sessionId = _uiState.value.session?.id ?: return
        val notes     = _uiState.value.notesDraft
        viewModelScope.launch {
            sessionRepo.updateNotes(sessionId, notes)
            _uiState.update { it.copy(showNotesSaved = true) }
            delay(1_500)
            _uiState.update { it.copy(showNotesSaved = false) }
        }
    }
}
