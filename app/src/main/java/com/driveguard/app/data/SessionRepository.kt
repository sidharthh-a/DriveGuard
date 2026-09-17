package com.driveguard.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Repository facade over Room for session history. */
class SessionRepository(private val dao: SessionDao) {

    fun observeSessions(): Flow<List<SessionSummary>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeLatest(): Flow<SessionSummary?> =
        dao.observeLatest().map { it?.toDomain() }

    suspend fun save(session: SessionEntity): Long = dao.insert(session)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun count(): Int = dao.count()
}
