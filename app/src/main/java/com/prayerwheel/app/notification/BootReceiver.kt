package com.prayerwheel.app.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Single rescheduling receiver: re-arms every app alarm (morning and evening
 * practice reminders plus the end-of-day summary) from persisted preferences
 * whenever existing alarms are lost or have gone stale.
 *
 * Handled actions:
 *  - [Intent.ACTION_BOOT_COMPLETED]: alarms do not survive a reboot.
 *  - [Intent.ACTION_TIMEZONE_CHANGED] / [Intent.ACTION_TIME_CHANGED]: alarms
 *    are stored as absolute instants computed from a local clock time, so after
 *    travel or a manual clock change they would fire at the wrong local time.
 *  - [Intent.ACTION_MY_PACKAGE_REPLACED]: an app update clears pending alarms
 *    on some devices, and re-arming picks up any scheduling changes.
 *  - [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED]
 *    (API 31+): sent when the user grants exact alarms, so the inexact
 *    fallbacks are upgraded to exact ones.
 *
 * The work runs via [runAsync] (goAsync plus a short, time-bounded coroutine)
 * so the DataStore reads never block the main thread.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action !in RESCHEDULE_ACTIONS) return

        val appContext = context.applicationContext
        runAsync(TAG) {
            // Each re-arm is isolated so one failure (e.g. an OEM AlarmManager
            // quirk) cannot prevent the other alarms from being restored.
            runCatching { ReminderScheduler.rescheduleAllFromPreferences(appContext) }
                .onFailure { e -> Log.e(TAG, "Failed to reschedule reminders (${intent.action})", e) }
            runCatching { EndOfDayScheduler.rescheduleFromPreferences(appContext) }
                .onFailure { e -> Log.e(TAG, "Failed to reschedule end-of-day alarm (${intent.action})", e) }
        }
    }

    private companion object {
        private const val TAG = "BootReceiver"

        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
