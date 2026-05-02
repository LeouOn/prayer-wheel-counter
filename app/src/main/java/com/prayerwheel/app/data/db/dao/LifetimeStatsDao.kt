package com.prayerwheel.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prayerwheel.app.data.model.LifetimeStats
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for lifetime statistics.
 *
 * There is only ever one row (id = 1) in the lifetime_stats table.
 * All cumulative totals are updated in place as sessions complete.
 */
@Dao
interface LifetimeStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: LifetimeStats)

    @Query("SELECT * FROM lifetime_stats WHERE id = 1")
    suspend fun getStats(): LifetimeStats?

    @Query("SELECT * FROM lifetime_stats WHERE id = 1")
    fun observeStats(): Flow<LifetimeStats?>

    @Query("DELETE FROM lifetime_stats")
    suspend fun deleteAll()
}
