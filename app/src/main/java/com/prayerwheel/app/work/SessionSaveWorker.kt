package com.prayerwheel.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prayerwheel.app.PrayerWheelApp
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
 * The session insert and lifetime-stats credit go through the same
 * transactional [com.prayerwheel.app.data.db.dao.LedgerDao.recordSession] as
 * the ViewModel, so the worker is serialized with every other writer.
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

        try {
            // One Room transaction for insert + lifetime credit: a crash between
            // them can't leave a row without its credit, and a failed run leaves
            // nothing inserted, so retries are safe. Duration is the VM's live
            // timer value passed in via input data.
            database.ledgerDao().recordSession(session, sessionDurationSeconds)
        } catch (e: android.database.sqlite.SQLiteFullException) {
            // Disk full — WorkManager's default retry policy would spin forever.
            // Fail this run so the user gets a real error elsewhere; retries
            // would not help until the user frees space.
            Log.e("SessionSaveWorker", "Disk full - session not saved", e)
            return Result.failure()
        } catch (e: Exception) {
            Log.e("SessionSaveWorker", "Failed to save session", e)
            return Result.failure()
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

        /** Cancels a queued (not yet run) session save, e.g. before "Reset All Data". */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

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
