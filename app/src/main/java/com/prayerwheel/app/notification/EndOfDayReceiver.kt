package com.prayerwheel.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.prayerwheel.app.PrayerWheelApp
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

/**
 * Receiver for the end-of-day summary alarm.
 *
 * Not exported: it is only ever fired by this app's own explicit
 * [android.app.PendingIntent] from [EndOfDayScheduler]. Re-arming after boot,
 * clock or time-zone changes is handled by [BootReceiver].
 *
 * On fire: if the user practiced that day, post ONE quiet, auto-dismissable
 * summary notification. If the user did NOT practice, stay completely
 * silent — no nagging, no streak-shaming. After firing (whether or not a
 * notification was posted), reschedule the next day's alarm so the reminder
 * never silently dies.
 *
 * Tone follows the project's no-gamification voice: no "streak", no "missed",
 * no "broken", no exclamation points.
 */
class EndOfDayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action != EndOfDayScheduler.ACTION_END_OF_DAY_SUMMARY) return

        // Reads from DataStore / Room. Run off the main thread via goAsync plus
        // a short, time-bounded coroutine so a cold read cannot cause an ANR.
        runAsync(TAG) {
            runCatching {
                handleEndOfDaySummary(context, intent)
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

    /**
     * Post the end-of-day summary if — and only if — the user practiced on the
     * day this alarm was scheduled for.
     *
     * The inexact fallback alarm (exact alarms are denied by default on
     * Android 14+) can be delivered just after midnight. Summarizing "today"
     * at that point would see the new, empty day and silently skip, so the
     * summary covers the alarm's target day instead (Dec 31 stays Dec 31 even
     * when delivered on Jan 1).
     *
     * CRITICAL: when there are no sessions that day, this method returns
     * without posting anything. No nagging, ever.
     */
    private suspend fun handleEndOfDaySummary(context: Context, intent: Intent) {
        val app = context.applicationContext as PrayerWheelApp
        val prefs = app.userPreferences
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()

        val targetDate = targetDateOf(intent, now, prefs)
        if (targetDate.isBefore(today.minusDays(1))) {
            // Delivered more than a day late (unusual); a summary that old
            // would be confusing, so stay silent.
            Log.d(TAG, "End-of-day alarm for $targetDate delivered on $today; skipping.")
            return
        }

        val startOfDay = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfNextDay = targetDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val sessions: List<Session> = app.database.sessionDao()
            .getSessionsBetween(startOfDay, startOfNextDay)
            .first()

        // CRITICAL: silent when no practice that day.
        if (sessions.isEmpty()) return

        val eveningHour = prefs.reminderEveningHour.first()
        val dayLabel = if (targetDate == today) "Today's practice" else "Yesterday's practice"
        val body = composeBody(sessions, eveningHour, dayLabel)

        postNotification(context, body)
    }

    /**
     * Devotional, plain-tone summary. Mirrors the project's no-gamification
     * voice: no "streak", no "missed", no "broken", no exclamation points.
     *
     * Slot attribution mirrors the plan's rule: a session is "morning" if its
     * startedAt hour < the evening reminder hour (default 19), else "evening".
     */
    private fun composeBody(sessions: List<Session>, eveningHour: Int, dayLabel: String): String {
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
            else -> "recorded" // unreachable in practice (every hour is in one bucket)
        }

        return "$dayLabel: $slotText. $formatted mantras accumulated."
    }

    private fun hourOf(epochMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * The local day this alarm was meant to summarize.
     *
     * Prefers [EndOfDayScheduler.EXTRA_TARGET_EPOCH_DAY]. Alarms armed before
     * that extra existed fall back to the date of the most recent occurrence
     * of the configured end-of-day time, so a 23:30 alarm delivered at 00:10
     * still summarizes the previous day. Never returns a future day.
     */
    private suspend fun targetDateOf(
        intent: Intent,
        now: ZonedDateTime,
        prefs: UserPreferences
    ): LocalDate {
        val today = now.toLocalDate()
        val fromExtra = intent.getLongExtra(EndOfDayScheduler.EXTRA_TARGET_EPOCH_DAY, Long.MIN_VALUE)
        if (fromExtra != Long.MIN_VALUE) {
            val date = LocalDate.ofEpochDay(fromExtra)
            return if (date.isAfter(today)) today else date
        }
        val scheduled = LocalTime.of(
            prefs.reminderEndOfDayHour.first(),
            prefs.reminderEndOfDayMinute.first()
        )
        return if (now.toLocalTime().isBefore(scheduled)) today.minusDays(1) else today
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
