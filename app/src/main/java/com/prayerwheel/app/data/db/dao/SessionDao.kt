package com.prayerwheel.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.prayerwheel.app.data.model.MantraStats
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.data.model.WheelStats
import com.prayerwheel.app.data.stats.SessionAggregateRow
import com.prayerwheel.app.data.stats.SessionAggregates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

/**
 * Data access object for prayer wheel sessions.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    /**
     * Targeted label edit. Never write a whole stale [Session] copy from the UI:
     * a resumed session's row is replaced in place, and a full-row update from
     * an old copy would silently revert its counts out of step with lifetime_stats.
     */
    @Query("UPDATE sessions SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String?)

    /** Adds the dedication chosen after a session ended (the session is recorded at its end). */
    @Query("UPDATE sessions SET dedication = :dedication WHERE session_key = :sessionKey")
    suspend fun updateDedicationByKey(sessionKey: String, dedication: String)

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

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    /**
     * Per-session columns for the Stats aggregates. Summed in Kotlin with
     * BigInteger ([SessionAggregates]) — never with SQL SUM(), which overflows
     * INT64 on BigInteger-as-TEXT mantra totals. Use the extension functions
     * below ([getMantraStats], [getWheelStats], [getStatsSince], ...).
     */
    @Query("SELECT mantraId, wheel_id AS wheelId, rotationCount, totalMantras FROM sessions WHERE startedAt >= :sinceTimestamp")
    fun observeAggregateRowsSince(sinceTimestamp: Long): Flow<List<SessionAggregateRow>>

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
 * Time-based statistics result. [totalMantras] is exact (BigInteger).
 */
data class TimeStats(
    val sessionCount: Long,
    val totalMantras: BigInteger
)

// Stats aggregates, computed in Kotlin with BigInteger (see observeAggregateRowsSince).

fun SessionDao.getMantraStats(): Flow<List<MantraStats>> = getMantraStatsSince(0L)

fun SessionDao.getMantraStatsSince(sinceTimestamp: Long): Flow<List<MantraStats>> =
    observeAggregateRowsSince(sinceTimestamp).map(SessionAggregates::byMantra)

fun SessionDao.getWheelStats(): Flow<List<WheelStats>> = getWheelStatsSince(0L)

fun SessionDao.getWheelStatsSince(sinceTimestamp: Long): Flow<List<WheelStats>> =
    observeAggregateRowsSince(sinceTimestamp).map(SessionAggregates::byWheel)

fun SessionDao.getStatsSince(sinceTimestamp: Long): Flow<TimeStats?> =
    observeAggregateRowsSince(sinceTimestamp).map(SessionAggregates::totals)
