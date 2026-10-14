package com.puttvision.core.data.repository

import com.puttvision.core.data.db.dao.PuttDeleteDao
import com.puttvision.core.data.db.dao.PuttMetricsDao
import com.puttvision.core.data.db.dao.PuttRecordDao
import com.puttvision.core.data.db.toDomain
import com.puttvision.core.data.db.toEntity
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttQuality
import com.puttvision.core.domain.model.PuttRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PuttRepository @Inject constructor(
    private val puttRecordDao : PuttRecordDao,
    private val puttMetricsDao: PuttMetricsDao,
    private val puttDeleteDao : PuttDeleteDao
) {
    // ── Records ──────────────────────────────────────────────────────────────

    suspend fun insertRecord(putt: PuttRecord): Long =
        puttRecordDao.insert(putt.toEntity())

    suspend fun updateRecord(putt: PuttRecord) =
        puttRecordDao.update(putt.toEntity())

    suspend fun updateQuality(puttId: Long, quality: PuttQuality) =
        puttRecordDao.updateQuality(puttId, quality.name)

    suspend fun updatePaths(puttId: Long, videoPath: String?, thumbPath: String?) =
        puttRecordDao.updatePaths(puttId, videoPath, thumbPath)

    suspend fun deletePutt(puttId: Long) =
        puttDeleteDao.deletePutt(puttId)

    fun observeBySession(sessionId: Long): Flow<List<PuttRecord>> =
        puttRecordDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeCountBySession(sessionId: Long): Flow<Int> =
        puttRecordDao.observeCountBySession(sessionId)

    suspend fun getBySession(sessionId: Long): List<PuttRecord> =
        puttRecordDao.getBySession(sessionId).map { it.toDomain() }

    suspend fun getRecord(id: Long): PuttRecord? =
        puttRecordDao.getById(id)?.toDomain()

    // ── Metrics ──────────────────────────────────────────────────────────────

    suspend fun upsertMetrics(metrics: PuttMetrics) =
        puttMetricsDao.insert(metrics.toEntity())

    suspend fun getMetrics(puttId: Long): PuttMetrics? =
        puttMetricsDao.getByPutt(puttId)?.toDomain()

    fun observeMetricsBySession(sessionId: Long): Flow<List<PuttMetrics>> =
        puttMetricsDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getMetricsBySession(sessionId: Long): List<PuttMetrics> =
        puttMetricsDao.getBySession(sessionId).map { it.toDomain() }

    // ── Aggregates ────────────────────────────────────────────────────────────

    suspend fun avgTempoRatio(sessionId: Long)    = puttMetricsDao.avgTempoRatio(sessionId)
    suspend fun avgHeadStability(sessionId: Long) = puttMetricsDao.avgHeadStability(sessionId)
    suspend fun avgFaceAngle(sessionId: Long)     = puttMetricsDao.avgFaceAngle(sessionId)
    suspend fun avgPathAngle(sessionId: Long)     = puttMetricsDao.avgPathAngle(sessionId)
}
