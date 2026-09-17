package com.driveguard.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMs: Long,
    val alertCount: Int,
    val monitoringInterruptions: Int,
    val avgInferenceMs: Float,
)

/** Domain-facing summary of a stored session. */
data class SessionSummary(
    val id: Long,
    val startedAtMs: Long,
    val durationMs: Long,
    val alertCount: Int,
    val monitoringInterruptions: Int,
    val avgInferenceMs: Float,
)

fun SessionEntity.toDomain(): SessionSummary = SessionSummary(
    id = id,
    startedAtMs = startedAtMs,
    durationMs = durationMs,
    alertCount = alertCount,
    monitoringInterruptions = monitoringInterruptions,
    avgInferenceMs = avgInferenceMs,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC LIMIT 1")
    fun observeLatest(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "driveguard.db",
                ).build().also { instance = it }
            }
    }
}
