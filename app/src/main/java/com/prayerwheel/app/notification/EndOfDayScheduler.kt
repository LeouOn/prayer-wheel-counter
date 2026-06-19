package com.prayerwheel.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.prayerwheel.app.PrayerWheelApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Self-contained end-of-day summary scheduler.
 *
 * Posts a single, quiet, auto-dismissable notification at a user-chosen time
 * summarizing today's practice — but ONLY if the user actually practiced.
 * If the user did not practice today, the alarm fires silently with no
 * notification (no nagging, no streak-shaming).
 *
 * This module is fully independent of T7 (ReminderScheduler): it owns its own
 * alarm, its own request code, and its own boot persistence. The notification
 * channel [CHANNEL_ID] is the SAME as T7's so the OS dedups it —
 * [NotificationManager.createNotificationChannel] is a no-op when a channel
 * with that ID already exists, so concurrent creation from T7 and T9 is safe.
 */
object EndOfDayScheduler {

    /** SAME channel ID as T7 — NotificationManager dedups by ID. */
    internal const val CHANNEL_ID = "practice_reminder_channel"
    private const val CHANNEL_NAME = "Practice Reminders"

    /** Custom fire action handled by [EndOfDayReceiver]. */
    const val ACTION_END_OF_DAY_SUMMARY = "com.prayerwheel.app.ACTION_END_OF_DAY_SUMMARY"

    /** Distinct from T7's request codes (2000-2003). */
    private const val REQUEST_CODE_END_OF_DAY = 3000

    private const val TAG = "EndOfDayScheduler"

    /**
     * Create the practice-reminder channel idempotently.
     *
     * [NotificationManager.createNotificationChannel] is a no-op when the
     * channel ID already exists, so this is safe to call from T7 and T9
     * concurrently — whichever runs first wins, the other is a no-op.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Gentle practice reminders and end-of-day summaries"
            setShowBadge(false)
        }
        NotificationManagerCompat.from(context)
            .createNotificationChannel(channel)
    }

    /**
     * Schedule (or reschedule) the next end-of-day summary alarm for [hour]:[minute].
     *
     * If that time has already passed today, the alarm is set for tomorrow at
     * the same time. The receiver self-reschedules on each fire, so this stays
     * in sync day over day.
     *
     * Uses [AlarmManager.setAlarmClock] for exact delivery when the exact-alarm
     * permission is available, falling back to [AlarmManager.setAndAllowWhileIdle]
     * on API 31+ when the user has not granted exact alarms (or if a
     * [SecurityException] surfaces at call time).
     */
    fun scheduleEndOfDaySummary(context: Context, hour: Int, minute: Int) {
        ensureChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerAt(hour, minute)
        val pendingIntent = pendingIntent(context)

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (exactAllowed) {
            try {
                val info = AlarmManager.AlarmClockInfo(triggerAt, null)
                alarmManager.setAlarmClock(info, pendingIntent)
                return
            } catch (e: SecurityException) {
                // Defensive: permission could be revoked between the check and call.
                Log.e(TAG, "Exact alarm denied at call time; falling back to inexact", e)
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    /** Cancel any pending end-of-day summary alarm. */
    fun cancelEndOfDaySummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    /**
     * Read the end-of-day reminder preferences and either schedule or cancel
     * the alarm to match. Safe to call from a BroadcastReceiver: the DataStore
     * reads are local and fast, wrapped in [runBlocking].
     */
    fun rescheduleFromPreferences(context: Context) {
        val prefs = (context.applicationContext as PrayerWheelApp).userPreferences
        runBlocking {
            val enabled = prefs.reminderEndOfDayEnabled.first()
            if (!enabled) {
                cancelEndOfDaySummary(context)
                return@runBlocking
            }
            val hour = prefs.reminderEndOfDayHour.first()
            val minute = prefs.reminderEndOfDayMinute.first()
            scheduleEndOfDaySummary(context, hour, minute)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, EndOfDayReceiver::class.java).apply {
            action = ACTION_END_OF_DAY_SUMMARY
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_END_OF_DAY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerAt(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val fire = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!fire.after(now)) {
            fire.add(Calendar.DAY_OF_YEAR, 1)
        }
        return fire.timeInMillis
    }
}
