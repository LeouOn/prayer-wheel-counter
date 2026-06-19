package com.prayerwheel.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * [BroadcastReceiver] that listens for [Intent.ACTION_BOOT_COMPLETED] so the app
 * can re-arm its scheduled reminders after a device restart.
 *
 * Alarms set via [android.app.AlarmManager] do not survive a reboot, so any
 * practice-reminder the user had scheduled must be rescheduled from persisted
 * preferences once the device finishes booting.
 *
 * The actual rescheduling logic is owned by T7 (ReminderScheduler). Until that
 * task lands, [rescheduleReminders] is a safe no-op stub that cannot crash —
 * see the TODO inside it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { rescheduleReminders(context) }
                .onFailure { e ->
                    Log.e(TAG, "Failed to reschedule reminders on boot", e)
                }
        }
    }

    /**
     * Re-arm all practice reminders from persisted preferences.
     *
     * Delegates to [ReminderScheduler.rescheduleAllFromPreferences], which
     * reads the reminder toggles and times from DataStore and reschedules the
     * morning/evening alarms via [android.app.AlarmManager.setAlarmClock].
     *
     * The call site wraps this in `runCatching` so that even if the scheduler
     * throws (e.g. AlarmManager in an unexpected state on some OEM ROMs), the
     * device's boot sequence cannot be disrupted.
     */
    private fun rescheduleReminders(context: Context) {
        ReminderScheduler.rescheduleAllFromPreferences(context)
    }

    private companion object {
        private const val TAG = "BootReceiver"
    }
}
