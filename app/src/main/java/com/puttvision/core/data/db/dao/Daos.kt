package com.puttvision.core.data.db.dao

import androidx.room.*
import com.puttvision.core.data.db.entity.SessionEntity
import com.puttvision.core.data.db.entity.PuttRecordEntity
import com.puttvision.core.data.db.entity.PuttMetricsEntity
import com.puttvision.core.data.db.entity.UserConfigEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// SessionDao
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): SessionEntity?

    /** Total sessions — used in STATS for evolution graphs */
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// PuttRecordDao
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface PuttRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(putt: PuttRecordEntity): Long

    @Update
    suspend fun update(putt: PuttRecordEntity)

    @Query("SELECT * FROM putt_records WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun observeBySession(sessionId: Long): Flow<List<PuttRecordEntity>>

    @Query("SELECT * FROM putt_records WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getBySession(sessionId: Long): List<PuttRecordEntity>

    @Query("SELECT * FROM putt_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PuttRecordEntity?

    @Query("SELECT COUNT(*) FROM putt_records WHERE session_id = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    /** Quick putt count used on the LIVE counter */
    @Query("SELECT COUNT(*) FROM putt_records WHERE session_id = :sessionId")
    fun observeCountBySession(sessionId: Long): Flow<Int>

    @Query("UPDATE putt_records SET quality = :quality WHERE id = :id")
    suspend fun updateQuality(id: Long, quality: String)

    @Query("UPDATE putt_records SET video_path = :path, thumbnail_path = :thumbPath WHERE id = :id")
    suspend fun updatePaths(id: Long, path: String?, thumbPath: String?)
}

// ─────────────────────────────────────────────────────────────────────────────
// PuttMetricsDao
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface PuttMetricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: PuttMetricsEntity)

    @Update
    suspend fun update(metrics: PuttMetricsEntity)

    @Query("SELECT * FROM putt_metrics WHERE putt_id = :puttId LIMIT 1")
    suspend fun getByPutt(puttId: Long): PuttMetricsEntity?

    @Query("""
        SELECT pm.* FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId
        ORDER BY pr.created_at ASC
    """)
    fun observeBySession(sessionId: Long): Flow<List<PuttMetricsEntity>>

    @Query("""
        SELECT pm.* FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId
    """)
    suspend fun getBySession(sessionId: Long): List<PuttMetricsEntity>

    // ── Aggregates for STATS screen ────────────────────────────────────────

    @Query("""
        SELECT AVG(tempo_ratio) FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId AND tempo_ratio IS NOT NULL
    """)
    suspend fun avgTempoRatio(sessionId: Long): Float?

    @Query("""
        SELECT AVG(head_stability) FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId AND head_stability IS NOT NULL
    """)
    suspend fun avgHeadStability(sessionId: Long): Float?

    @Query("""
        SELECT AVG(face_angle_deg) FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId AND face_angle_deg IS NOT NULL
    """)
    suspend fun avgFaceAngle(sessionId: Long): Float?

    @Query("""
        SELECT AVG(path_angle_deg) FROM putt_metrics pm
        INNER JOIN putt_records pr ON pm.putt_id = pr.id
        WHERE pr.session_id = :sessionId AND path_angle_deg IS NOT NULL
    """)
    suspend fun avgPathAngle(sessionId: Long): Float?
}

// ─────────────────────────────────────────────────────────────────────────────
// UserConfigDao
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface UserConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: UserConfigEntity)

    @Query("SELECT * FROM user_config WHERE id = 1 LIMIT 1")
    suspend fun get(): UserConfigEntity?

    @Query("SELECT * FROM user_config WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserConfigEntity?>
}

// ── Extra delete queries added in v4 ──────────────────────────────────────────

// PuttRecordDao
// (Add to the existing @Dao interface body via new extension file to avoid
//  rewriting the whole file. Room merges multiple @Dao interfaces per entity.)

@Dao
interface PuttDeleteDao {

    @Query("DELETE FROM putt_records WHERE id = :puttId")
    suspend fun deletePutt(puttId: Long)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM putt_records WHERE session_id = :sessionId")
    suspend fun puttCountForSession(sessionId: Long): Int
}
