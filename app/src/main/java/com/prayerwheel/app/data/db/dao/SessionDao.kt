package com.prayerwheel.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.prayerwheel.app.data.model.MantraStats
import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for prayer wheel sessions.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    /**
     * T17: Most recently started session (one-shot, suspend), or null.
     * Powers the resume-after-kill banner in WheelViewModel.detectResumableSession.
     */
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecentSession(): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<Session>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT SUM(totalMantras) FROM sessions")
    suspend fun getTotalMantrasFromSessions(): Long?

    @Query("SELECT SUM(rotationCount) FROM sessions")
    suspend fun getTotalRotationsFromSessions(): Long?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT mantraId, COUNT(*) as sessionCount, SUM(rotationCount) as totalRotations, SUM(totalMantras) as totalMantras FROM sessions GROUP BY mantraId")
    fun getMantraStats(): Flow<List<MantraStats>>

    @Query("SELECT mantraId, COUNT(*) as sessionCount, SUM(rotationCount) as totalRotations, SUM(totalMantras) as totalMantras FROM sessions WHERE startedAt >= :sinceTimestamp GROUP BY mantraId")
    fun getMantraStatsSince(sinceTimestamp: Long): Flow<List<MantraStats>>

    @Query("SELECT wheel_id as wheelId, COUNT(*) as sessionCount, SUM(rotationCount) as totalRotations, SUM(totalMantras) as totalMantras FROM sessions GROUP BY wheel_id")
    fun getWheelStats(): Flow<List<com.prayerwheel.app.data.model.WheelStats>>

    @Query("SELECT wheel_id as wheelId, COUNT(*) as sessionCount, SUM(rotationCount) as totalRotations, SUM(totalMantras) as totalMantras FROM sessions WHERE startedAt >= :sinceTimestamp GROUP BY wheel_id")
    fun getWheelStatsSince(sinceTimestamp: Long): Flow<List<com.prayerwheel.app.data.model.WheelStats>>

    @Query("SELECT COUNT(*) as sessionCount, SUM(totalMantras) as totalMantras FROM sessions WHERE startedAt >= :sinceTimestamp")
    fun getStatsSince(sinceTimestamp: Long): Flow<TimeStats?>

    @Query("SELECT * FROM sessions WHERE startedAt >= :sinceTimestamp ORDER BY startedAt DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE startedAt >= :startDate AND startedAt <= :endDate ORDER BY startedAt ASC")
    fun getSessionsBetween(startDate: Long, endDate: Long): Flow<List<Session>>

    /**
     * Observes all sessions that started within a single day window, used by the
     * Today's Progress card (T8). The window is supplied as a half-open
     * `[startOfDay, startOfNextDay)` range in epoch millis so callers can compute
     * the precise local-day boundaries.
     */
    @Query("SELECT * FROM sessions WHERE startedAt >= :startOfDay AND startedAt < :startOfNextDay ORDER BY startedAt")
    fun observeSessionsForDay(startOfDay: Long, startOfNextDay: Long): Flow<List<Session>>

}

/**
 * Time-based statistics result.
 */
data class TimeStats(
    val sessionCount: Long,
    val totalMantras: Long
)
