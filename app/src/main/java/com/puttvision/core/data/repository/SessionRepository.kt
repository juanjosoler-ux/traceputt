package com.puttvision.core.data.repository

import com.puttvision.core.data.db.dao.PuttDeleteDao
import com.puttvision.core.data.db.dao.SessionDao
import com.puttvision.core.data.db.toDomain
import com.puttvision.core.data.db.toEntity
import com.puttvision.core.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao   : SessionDao,
    private val puttDeleteDao: PuttDeleteDao
) {
    fun observeAll(): Flow<List<Session>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun createSession(session: Session): Long =
        sessionDao.insert(session.toEntity())

    suspend fun updateSession(session: Session) =
        sessionDao.update(session.toEntity())

    suspend fun updateNotes(sessionId: Long, notes: String) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.update(existing.copy(notes = notes))
    }

    suspend fun deleteSession(sessionId: Long) =
        puttDeleteDao.deleteSession(sessionId)

    suspend fun getById(id: Long): Session? =
        sessionDao.getById(id)?.toDomain()

    suspend fun getLatest(): Session? =
        sessionDao.getLatest()?.toDomain()

    suspend fun totalSessions(): Int =
        sessionDao.count()
}
