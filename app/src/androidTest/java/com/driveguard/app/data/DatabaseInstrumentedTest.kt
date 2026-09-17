package com.driveguard.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private fun db(): AppDatabase =
        androidx.room.Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()

    @Test
    fun insertAndQueryRoundTrip() = runBlocking {
        val database = db()
        val dao = database.sessionDao()
        val id = dao.insert(
            SessionEntity(
                startedAtMs = 1_000L,
                endedAtMs = 61_000L,
                durationMs = 60_000L,
                alertCount = 2,
                monitoringInterruptions = 1,
                avgInferenceMs = 12.5f,
            ),
        )
        assertThat(id).isGreaterThan(0)
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].alertCount).isEqualTo(2)
        assertThat(all[0].durationMs).isEqualTo(60_000)
        database.close()
    }

    @Test
    fun latestIsMostRecent() = runBlocking {
        val database = db()
        val dao = database.sessionDao()
        dao.insert(SessionEntity(startedAtMs = 1_000, endedAtMs = 2_000, durationMs = 1_000, alertCount = 0, monitoringInterruptions = 0, avgInferenceMs = 0f))
        dao.insert(SessionEntity(startedAtMs = 9_000, endedAtMs = 10_000, durationMs = 1_000, alertCount = 0, monitoringInterruptions = 0, avgInferenceMs = 0f))
        val latest = dao.observeLatest().first()
        assertThat(latest!!.startedAtMs).isEqualTo(9_000)
        database.close()
    }

    @Test
    fun deleteAndClearWork() = runBlocking {
        val database = db()
        val dao = database.sessionDao()
        val id = dao.insert(SessionEntity(startedAtMs = 0, endedAtMs = 1, durationMs = 1, alertCount = 0, monitoringInterruptions = 0, avgInferenceMs = 0f))
        dao.deleteById(id)
        assertThat(dao.count()).isEqualTo(0)
        dao.insert(SessionEntity(startedAtMs = 0, endedAtMs = 1, durationMs = 1, alertCount = 0, monitoringInterruptions = 0, avgInferenceMs = 0f))
        dao.clearAll()
        assertThat(dao.count()).isEqualTo(0)
        database.close()
    }
}
