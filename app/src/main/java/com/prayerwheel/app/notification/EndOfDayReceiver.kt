package com.prayerwheel.app.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prayerwheel.app.PrayerWheelApp
import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.util.Calendar

/**
 * Receiver for the end-of-day summary alarm and for [Intent.ACTION_BOOT_COMPLETED].
 *
 * Two responsibilities, both self-contained and independent of T7
 * (ReminderScheduler / ReminderReceiver / BootReceiver):
 *
 * 1. **Boot**: after a reboot, re-arm the end-of-day alarm from persisted
 *    preferences so end-of-day summaries survive restarts independently.
 *    T7's [com.prayerwheel.app.notification.BootReceiver] does the same for its
 *    own alarms; the two registrations do not collide because Android allows
 *    multiple receivers for the same system action.
 *
 * 2. **End-of-day fire**: if the user practiced today, post ONE quiet,
 *    auto-dismissable summary notification. If the user did NOT practice,
 *    stay completely silent — no nagging, no streak-shaming. After firing
 *    (whether or not a notification was posted), reschedule the next day's
 *    alarm so the reminder never silently dies.
 *
 * Tone follows the project's no-gamification voice: no "streak", no "missed",
 * no "broken", no exclamation points.
 */
class EndOfDayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                runCatching {
                    EndOfDayScheduler.rescheduleFromPreferences(context)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to reschedule end-of-day alarm on boot", e)
                }
            }

            EndOfDayScheduler.ACTION_END_OF_DAY_SUMMARY -> {
                runCatching {
                    handleEndOfDaySummary(context)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to handle end-of-day summary", e)
                }
                // Always re-arm tomorrow, even if this fire failed or the user
                // did not practice, so the reminder never silently disappears.
                runCatching {
                    EndOfDayScheduler.rescheduleFromPreferences(context)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to reschedule next end-of-day alarm", e)
                }
            }
        }
    }

    /**
     * Post the end-of-day summary if — and only if — the user practiced today.
     *
     * CRITICAL: when there are no sessions today, this method returns without
     * posting anything. No nagging, ever.
     */
    private fun handleEndOfDaySummary(context: Context) {
        val app = context.applicationContext as PrayerWheelApp
        val prefs = app.userPreferences

        val (startOfDay, startOfNextDay) = todayBounds()

        val sessions: List<Session> = runBlocking {
            app.database.sessionDao()
                .getSessionsBetween(startOfDay, startOfNextDay)
                .first()
        }

        // CRITICAL: silent when no practice today.
        if (sessions.isEmpty()) return

        val eveningHour = runBlocking { prefs.reminderEveningHour.first() }
        val body = composeBody(sessions, eveningHour)

        postNotification(context, body)
    }

    /**
     * Devotional, plain-tone summary. Mirrors the project's no-gamification
     * voice: no "streak", no "missed", no "broken", no exclamation points.
     *
     * Slot attribution mirrors the plan's rule: a session is "morning" if its
     * startedAt hour < the evening reminder hour (default 19), else "evening".
     */
    private fun composeBody(sessions: List<Session>, eveningHour: Int): String {
        val totalMantras = sessions.fold(BigInteger.ZERO) { acc, s ->
            acc + s.totalMantras
        }
        val formatted = SessionNotificationService.formatMantraCount(totalMantras)

        val morningDone = sessions.any { hourOf(it.startedAt) < eveningHour }
        val eveningDone = sessions.any { hourOf(it.startedAt) >= eveningHour }

        val slotText = when {
            morningDone && eveningDone -> "morning and evening"
            morningDone -> "morning"
            eveningDone -> "evening"
            else -> "today" // unreachable in practice (every hour is in one bucket)
        }

        return "Today's practice: $slotText. $formatted mantras accumulated."
    }

    private fun hourOf(epochMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun todayBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        @Suppress("UNCHECKED_CAST")
        val end = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun postNotification(context: Context, body: String) {
        EndOfDayScheduler.ensureChannel(context)

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPending = openIntent?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Prayer Wheel")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(false)                       // auto-dismissable
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { e ->
            // e.g. POST_NOTIFICATIONS not granted on API 33+: fail quiet.
            Log.e(TAG, "Failed to post end-of-day summary notification", e)
        }
    }

    private companion object {
        private const val TAG = "EndOfDayReceiver"
        private const val NOTIFICATION_ID = 3001
        private const val REQUEST_CODE_OPEN_APP = 3002

        /** Same ID as the scheduler / T7 — channel dedups by ID. */
        private const val CHANNEL_ID = "practice_reminder_channel"
    }
}
