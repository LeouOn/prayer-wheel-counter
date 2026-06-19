package com.prayerwheel.app.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prayerwheel.app.PrayerWheelApp
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session
import java.math.BigInteger

/**
 * Persists an in-flight prayer wheel session when the app is killed while a
 * session is active.
 *
 * Replaces the previous `WheelViewModel.onCleared` implementation that used
 * `GlobalScope.launch(Dispatchers.IO)`, which is fragile: the process can be
 * torn down before the coroutine finishes the Room write, silently losing the
 * session. WorkManager is process-death-safe — it will retry the work in a
 * fresh process if needed.
 *
 * Limitation: because no UI context is available at this point, the session is
 * saved with `dedication = null`. The user can edit/add a dedication later from
 * the History screen.
 *
 * The lifetime-stats update mirrors `WheelViewModel.updateLifetimeStats`:
 * `totalSpinningTimeSeconds` and `averageSessionDurationSeconds` are carried
 * forward (the bug fixed in T5 — where every live rotation clobbered those
 * fields — must not regress via this code path).
 */
class SessionSaveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedAt = inputData.getLong(KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return Result.success() // nothing to save

        val rotations = inputData.getLong(KEY_ROTATIONS, 0L)
        if (rotations == 0L) return Result.success() // no rotations => no session

        val endedAt = inputData.getLong(KEY_ENDED_AT, startedAt)
        val mantrasPerRotation = inputData.getLong(KEY_MANTRAS_PER_ROTATION, 1L)
        // BigInteger is not a supported androidx.work.Data type, so it is
        // marshalled as String and reconstituted here.
        val totalMantras = BigInteger(inputData.getString(KEY_TOTAL_MANTRAS) ?: "0")
        val mantraId = inputData.getString(KEY_MANTRA_ID) ?: ""
        val mode = inputData.getString(KEY_MODE) ?: "MANUAL"
        val averageRpm = inputData.getFloat(KEY_AVERAGE_RPM, 0f)
        val peakRpm = inputData.getFloat(KEY_PEAK_RPM, 0f)
        val intention = inputData.getString(KEY_INTENTION)?.takeIf { it.isNotBlank() }
        // Sentinel -1L encodes "no session goal" since Data has no nullable Long.
        val sessionGoal = inputData.getLong(KEY_SESSION_GOAL, -1L).takeIf { it > 0 }
        val wheelId = inputData.getString(KEY_WHEEL_ID)?.takeIf { it.isNotBlank() }
        val label = inputData.getString(KEY_LABEL)?.takeIf { it.isNotBlank() }
        val sessionDurationSeconds = inputData.getLong(KEY_SESSION_DURATION_SECONDS, 0L)

        val session = Session(
            startedAt = startedAt,
            endedAt = endedAt,
            rotationCount = rotations,
            mantrasPerRotation = mantrasPerRotation,
            totalMantras = totalMantras,
            mantraId = mantraId,
            dedication = null, // No UI context on app-kill; user can edit in History.
            mode = mode,
            averageRpm = averageRpm,
            peakRpm = peakRpm,
            totalSpins = rotations,
            intention = intention,
            sessionGoal = sessionGoal,
            wheelId = wheelId,
            label = label
        )

        val database = (applicationContext as PrayerWheelApp).database
        val sessionDao = database.sessionDao()
        val lifetimeStatsDao = database.lifetimeStatsDao()

        // Wrap both writes in a single Room transaction. Without this, a crash
        // between `sessionDao.insert` and `lifetimeStatsDao.upsert` lets
        // WorkManager retry the worker, re-running `insert` and producing a
        // duplicate session row. Atomicity here means a failed transaction
        // leaves nothing inserted, so retries are safe.
        database.withTransaction {
            sessionDao.insert(session)

            val existing = lifetimeStatsDao.getStats()
            val newStats = existing?.copy(
                totalRotations = existing.totalRotations + session.rotationCount,
                totalMantras = existing.totalMantras + session.totalMantras,
                sessionsCompleted = existing.sessionsCompleted + 1,
                totalSpinningTimeSeconds = existing.totalSpinningTimeSeconds + sessionDurationSeconds,
                averageSessionDurationSeconds = if (existing.sessionsCompleted > 0) {
                    (existing.totalSpinningTimeSeconds + sessionDurationSeconds) /
                        (existing.sessionsCompleted + 1)
                } else {
                    sessionDurationSeconds
                }
            ) ?: LifetimeStats(
                id = 1,
                totalRotations = session.rotationCount,
                totalMantras = session.totalMantras,
                sessionsCompleted = 1,
                firstSessionAt = session.startedAt,
                totalSpinningTimeSeconds = sessionDurationSeconds,
                averageSessionDurationSeconds = sessionDurationSeconds
            )
            lifetimeStatsDao.upsert(newStats)
        }

        return Result.success()
    }

    companion object {
        // Unique work name: prevents double-insertion if onCleared fires
        // repeatedly (e.g. process death + relaunch race, or rapid force-stops).
        const val UNIQUE_WORK_NAME = "session_save_on_clear"

        const val KEY_STARTED_AT = "started_at"
        const val KEY_ENDED_AT = "ended_at"
        const val KEY_ROTATIONS = "rotations"
        const val KEY_MANTRAS_PER_ROTATION = "mantras_per_rotation"

        // BigInteger must be marshalled as String (Data has no BigInteger type).
        const val KEY_TOTAL_MANTRAS = "total_mantras"
        const val KEY_MANTRA_ID = "mantra_id"
        const val KEY_MODE = "mode"
        const val KEY_AVERAGE_RPM = "average_rpm"
        const val KEY_PEAK_RPM = "peak_rpm"
        const val KEY_INTENTION = "intention"
        const val KEY_SESSION_GOAL = "session_goal"
        const val KEY_WHEEL_ID = "wheel_id"
        const val KEY_LABEL = "label"
        const val KEY_SESSION_DURATION_SECONDS = "session_duration_seconds"

        /**
         * Builds the [androidx.work.OneTimeWorkRequest] for saving the in-flight
         * session and enqueues it as unique work with [ExistingWorkPolicy.KEEP]
         * under [UNIQUE_WORK_NAME]. KEEP (rather than REPLACE) ensures that once
         * a save has completed, a second `onCleared`/relaunch race does not
         * re-execute the work and insert a duplicate session.
         *
         * @return `true` if work was enqueued; `false` if there is no session to
         *   save ([startedAt] is null or [rotations] is 0, matching the previous
         *   `createSessionFromCurrentState` guard).
         */
        fun enqueue(
            context: Context,
            startedAt: Long?,
            rotations: Long,
            mantrasPerRotation: Long,
            totalMantras: BigInteger,
            mantraId: String,
            mode: String,
            averageRpm: Float,
            peakRpm: Float,
            intention: String?,
            sessionGoal: Long?,
            wheelId: String?,
            label: String?,
            sessionDurationSeconds: Long
        ): Boolean {
            if (startedAt == null || rotations == 0L) return false

            val data = workDataOf(
                KEY_STARTED_AT to startedAt,
                KEY_ENDED_AT to System.currentTimeMillis(),
                KEY_ROTATIONS to rotations,
                KEY_MANTRAS_PER_ROTATION to mantrasPerRotation,
                KEY_TOTAL_MANTRAS to totalMantras.toString(),
                KEY_MANTRA_ID to mantraId,
                KEY_MODE to mode,
                KEY_AVERAGE_RPM to averageRpm,
                KEY_PEAK_RPM to peakRpm,
                KEY_INTENTION to (intention ?: ""),
                KEY_SESSION_GOAL to (sessionGoal ?: -1L),
                KEY_WHEEL_ID to (wheelId ?: ""),
                KEY_LABEL to (label ?: ""),
                KEY_SESSION_DURATION_SECONDS to sessionDurationSeconds
            )

            val request = OneTimeWorkRequestBuilder<SessionSaveWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)

            return true
        }
    }
}
