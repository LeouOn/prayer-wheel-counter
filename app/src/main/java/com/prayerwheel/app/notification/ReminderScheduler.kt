package com.prayerwheel.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prayerwheel.app.PrayerWheelApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Schedules the user's morning and evening practice reminders via
 * [AlarmManager.setAlarmClock].
 *
 * [AlarmManager.setAlarmClock] is deliberately chosen over `setRepeating` or
 * `setInexactRepeating` because:
 *  - it bypasses Doze mode, firing at the requested instant, and
 *  - the OS treats it as a user-initiated alarm (the strictest, most reliable
 *    tier), which is the correct semantic for a clock-time practice reminder
 *    the user explicitly requested.
 *
 * Because `setAlarmClock` alarms are one-shot (a hard constraint on API 19+),
 * the [ReminderReceiver] re-arms the next day's alarm each time one fires. This
 * also makes the daily cadence resilient to the user changing their reminder
 * time — only the most recently scheduled alarm survives.
 *
 * Alarms do NOT survive a device reboot; [rescheduleAllFromPreferences] is
 * invoked by [BootReceiver] after `BOOT_COMPLETED` to re-arm them from the
 * persisted DataStore preferences.
 *
 * On API 31+ (S) the app must hold the `SCHEDULE_EXACT_ALARM` (or
 * `USE_EXACT_ALARM`) permission to call `setAlarmClock`. If the user has
 * revoked it, we gracefully fall back to [AlarmManager.setAndAllowWhileIdle]
 * (inexact, but still fires within a Doze maintenance window) and log the
 * fallback — we never crash.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    // ── Broadcast actions (one per reminder slot) ───────────────────────────
    const val ACTION_MORNING_REMINDER = "com.prayerwheel.app.ACTION_MORNING_REMINDER"
    const val ACTION_EVENING_REMINDER = "com.prayerwheel.app.ACTION_EVENING_REMINDER"

    // ── Notification channel (distinct from SessionNotificationService) ─────
    /**
     * Channel id for practice reminders. Deliberately distinct from
     * `SessionNotificationService.CHANNEL_ID` ("session_notification_channel")
     * so the user can independently mute the quiet ongoing-session
     * notification while keeping audible morning/evening reminders.
     */
    const val CHANNEL_ID = "practice_reminder_channel"
    private const val CHANNEL_NAME = "Practice Reminders"
    private const val CHANNEL_DESCRIPTION =
        "Morning and evening reminders to invite a moment of practice"

    // ── PendingIntent request codes (distinct per slot so they never collide) ──
    // 2000-range is T7's contract (see EndOfDayScheduler's comment: "Distinct
    // from T7's request codes (2000-2003)"); T9 owns the 3000-range. Keeping
    // the ranges separate makes `dumpsys alarm` output unambiguous.
    private const val REQUEST_CODE_MORNING = 2000
    private const val REQUEST_CODE_EVENING = 2001

    // ── Public scheduling API ───────────────────────────────────────────────

    /**
     * Schedules (or reschedules) the morning practice reminder for the next
     * occurrence of [hour]:[minute]. If the time has already passed today the
     * alarm is set for tomorrow.
     */
    fun scheduleMorningReminder(context: Context, hour: Int, minute: Int) {
        scheduleReminder(
            context = context,
            action = ACTION_MORNING_REMINDER,
            requestCode = REQUEST_CODE_MORNING,
            hour = hour,
            minute = minute
        )
    }

    /**
     * Schedules (or reschedules) the evening practice reminder for the next
     * occurrence of [hour]:[minute].
     */
    fun scheduleEveningReminder(context: Context, hour: Int, minute: Int) {
        scheduleReminder(
            context = context,
            action = ACTION_EVENING_REMINDER,
            requestCode = REQUEST_CODE_EVENING,
            hour = hour,
            minute = minute
        )
    }

    /** Cancels any pending morning reminder alarm. Safe to call when none is set. */
    fun cancelMorningReminder(context: Context) {
        cancelReminder(context, ACTION_MORNING_REMINDER, REQUEST_CODE_MORNING)
    }

    /** Cancels any pending evening reminder alarm. Safe to call when none is set. */
    fun cancelEveningReminder(context: Context) {
        cancelReminder(context, ACTION_EVENING_REMINDER, REQUEST_CODE_EVENING)
    }

    /**
     * Re-arms all practice reminders from the persisted DataStore preferences.
     *
     * This is called synchronously from [BootReceiver.onReceive] after a device
     * reboot, so it reads the relevant preference flows via [runBlocking].
     * DataStore caches its latest snapshot in memory after first access, so the
     * blocking read is brief. Reminders that the user has disabled are
     * explicitly cancelled to avoid orphan alarms.
     */
    fun rescheduleAllFromPreferences(context: Context) {
        val appContext = context.applicationContext
        val config = readConfig(appContext)

        if (config.morningEnabled) {
            scheduleMorningReminder(appContext, config.morningHour, config.morningMinute)
        } else {
            cancelMorningReminder(appContext)
        }

        if (config.eveningEnabled) {
            scheduleEveningReminder(appContext, config.eveningHour, config.eveningMinute)
        } else {
            cancelEveningReminder(appContext)
        }
    }

    /**
     * Idempotently creates the [CHANNEL_ID] notification channel. Safe to call
     * repeatedly; the system dedupes by id. Owned by T7 but may be referenced
     * by the end-of-day scheduler (T9).
     *
     * Importance is [android.app.NotificationManager.IMPORTANCE_LOW] (quiet,
     * non-intrusive) to honour the project's no-gamification "soft/subdued"
     * voice. This MUST match [EndOfDayScheduler.ensureChannel]'s importance —
     * since both modules share [CHANNEL_ID], the first creator wins and a
     * mismatch would make the channel's actual importance depend on module
     * init order (a silent race).
     */
    fun ensurePracticeReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val manager = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ── Internal scheduling mechanics ───────────────────────────────────────

    private fun scheduleReminder(
        context: Context,
        action: String,
        requestCode: Int,
        hour: Int,
        minute: Int
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = nextOccurrence(hour, minute)
        val pendingIntent = buildReminderPendingIntent(appContext, action, requestCode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            // SCHEDULE_EXACT_ALARM revoked by the user on API 31+. Fall back to
            // an inexact alarm — it will still fire, just within a Doze
            // maintenance window near the requested time. Never crash.
            Log.w(TAG, "Exact alarm denied; falling back to inexact setAndAllowWhileIdle")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            // Primary path: setAlarmClock bypasses Doze and is treated by the
            // OS as a user-initiated alarm. Correct for clock-time reminders.
            // Defensive try/catch matches EndOfDayScheduler: canScheduleExactAlarms()
            // can flip to false between the check and the call (revoked via
            // settings in a race) — fall back to inexact rather than crash.
            try {
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, null)
                alarmManager.setAlarmClock(info, pendingIntent)
            } catch (e: SecurityException) {
                Log.w(TAG, "setAlarmClock threw SecurityException; falling back to inexact", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        Log.d(
            TAG,
            "Scheduled $action for ${formatForLog(hour, minute)} " +
                "(trigger epoch ms = $triggerAtMillis)"
        )
    }

    private fun cancelReminder(context: Context, action: String, requestCode: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildReminderPendingIntent(appContext, action, requestCode)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled $action")
    }

    /**
     * Builds the broadcast [PendingIntent] that fires [ReminderReceiver] for
     * the given [action]. Uses [PendingIntent.FLAG_IMMUTABLE] (required on
     * API 23+, which minSdk 26 satisfies) combined with FLAG_UPDATE_CURRENT so
     * repeated scheduling with new trigger times updates the existing alarm
     * slot rather than stacking duplicates.
     */
    private fun buildReminderPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            setClass(context, ReminderReceiver::class.java)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Returns the epoch-ms of the next occurrence of [hour]:[minute:00]. If
     * that time has already passed today, returns tomorrow's occurrence so a
     * reminder scheduled "for today" never fires immediately in the past.
     */
    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /**
     * Reads the reminder configuration synchronously. Intended for use from
     * contexts without a coroutine scope (e.g. [BootReceiver.onReceive]).
     */
    private fun readConfig(context: Context): ReminderConfig {
        val userPreferences = (context.applicationContext as PrayerWheelApp).userPreferences
        return runBlocking {
            ReminderConfig(
                morningEnabled = userPreferences.reminderMorningEnabled.first(),
                morningHour = userPreferences.reminderMorningHour.first(),
                morningMinute = userPreferences.reminderMorningMinute.first(),
                eveningEnabled = userPreferences.reminderEveningEnabled.first(),
                eveningHour = userPreferences.reminderEveningHour.first(),
                eveningMinute = userPreferences.reminderEveningMinute.first()
            )
        }
    }

    private fun formatForLog(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    /** Snapshot of the reminder-related persisted preferences. */
    private data class ReminderConfig(
        val morningEnabled: Boolean,
        val morningHour: Int,
        val morningMinute: Int,
        val eveningEnabled: Boolean,
        val eveningHour: Int,
        val eveningMinute: Int
    )
}
