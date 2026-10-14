package com.puttvision.feature.stats

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puttvision.core.data.export.PdfExporter
import com.puttvision.core.data.export.SessionExporter
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttQuality
import com.puttvision.core.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SessionStats(
    val totalPutts      : Int         = 0,
    val goodPutts       : Int         = 0,
    val badPutts        : Int         = 0,
    val avgTempoRatio   : Float?      = null,
    val avgHeadStability: Float?      = null,
    val avgFaceAngle    : Float?      = null,
    val avgPathAngle    : Float?      = null,
    val tempoSeries     : List<Float> = emptyList(),
    val headSeries      : List<Float> = emptyList(),
    val faceSeries      : List<Float> = emptyList(),
    val pathSeries      : List<Float> = emptyList()
)

data class StatsUiState(
    val session      : Session?      = null,
    val stats        : SessionStats  = SessionStats(),
    val allSessions  : List<Session> = emptyList(),
    val isLoading    : Boolean       = true,
    val error        : String?       = null,
    /** One-shot share intent — consumed after launch */
    val exportIntent : Intent?       = null,
    val isExporting  : Boolean       = false
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val puttRepo   : PuttRepository,
    private val csvExporter: SessionExporter,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load(sessionId: Long?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val allSessions = sessionRepo.observeAll().first()
                val targetId    = sessionId ?: allSessions.firstOrNull()?.id
                if (targetId == null) {
                    _uiState.update { it.copy(isLoading = false, allSessions = allSessions) }
                    return@launch
                }
                val session = sessionRepo.getById(targetId)
                val putts   = puttRepo.getBySession(targetId)
                val metrics = puttRepo.getMetricsBySession(targetId)

                _uiState.update {
                    it.copy(
                        session     = session,
                        allSessions = allSessions,
                        stats       = buildStats(
                            putts.size,
                            putts.count { r -> r.quality == PuttQuality.GOOD },
                            putts.count { r -> r.quality == PuttQuality.BAD },
                            metrics,
                            puttRepo.avgTempoRatio(targetId),
                            puttRepo.avgHeadStability(targetId),
                            puttRepo.avgFaceAngle(targetId),
                            puttRepo.avgPathAngle(targetId)
                        ),
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun switchSession(id: Long) = load(id)

    fun exportCsv() {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val intent = csvExporter.exportSession(sessionId)
            _uiState.update { it.copy(exportIntent = intent, isExporting = false) }
        }
    }

    fun exportPdf() {
        val sessionId = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val intent = pdfExporter.exportSession(sessionId)
            _uiState.update { it.copy(exportIntent = intent, isExporting = false) }
        }
    }

    fun onExportConsumed() = _uiState.update { it.copy(exportIntent = null) }

    private fun buildStats(
        total: Int, good: Int, bad: Int,
        metrics: List<PuttMetrics>,
        avgT: Float?, avgH: Float?, avgF: Float?, avgP: Float? = null
    ) = SessionStats(
        totalPutts       = total,
        goodPutts        = good,
        badPutts         = bad,
        avgTempoRatio    = avgT,
        avgHeadStability = avgH,
        avgFaceAngle     = avgF,
        avgPathAngle     = avgP,
        tempoSeries      = metrics.mapNotNull { it.tempoRatio },
        headSeries       = metrics.mapNotNull { it.headStability },
        faceSeries       = metrics.mapNotNull { it.faceAngleDeg },
        pathSeries       = metrics.mapNotNull { it.pathAngleDeg }
    )
}
