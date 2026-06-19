package com.prayerwheel.app.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prayerwheel.app.PrayerWheelApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * [BroadcastReceiver] fired by [android.app.AlarmManager] when a scheduled
 * practice reminder is due.
 *
 * On receive it:
 *  1. Ensures the `practice_reminder_channel` notification channel exists.
 *  2. Posts a plain, devotional notification (no streak/gamification language,
 *     no exclamation marks). If the user has set an intention it is appended in
 *     italics, in a subdued tone.
 *  3. Self-reschedules the NEXT day's alarm for the same slot —
 *     [android.app.AlarmManager.setAlarmClock] alarms are one-shot, so this
 *     self-reschedule is what produces the daily cadence.
 *
 * If the user has since disabled the reminder, the receiver neither notifies
 * nor reschedules, so an in-flight alarm cleanly stops the cycle.
 *
 * Tapping the notification opens MainActivity via the launcher intent.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val appContext = context.applicationContext

        when (intent.action) {
            ReminderScheduler.ACTION_MORNING_REMINDER -> handleReminder(appContext, isMorning = true)
            ReminderScheduler.ACTION_EVENING_REMINDER -> handleReminder(appContext, isMorning = false)
            else -> return
        }
    }

    private fun handleReminder(context: Context, isMorning: Boolean) {
        val userPreferences = (context as PrayerWheelApp).userPreferences
        val snapshot = runBlocking {
            ReminderSnapshot(
                enabled = if (isMorning) {
                    userPreferences.reminderMorningEnabled.first()
                } else {
                    userPreferences.reminderEveningEnabled.first()
                },
                hour = if (isMorning) {
                    userPreferences.reminderMorningHour.first()
                } else {
                    userPreferences.reminderEveningHour.first()
                },
                minute = if (isMorning) {
                    userPreferences.reminderMorningMinute.first()
                } else {
                    userPreferences.reminderEveningMinute.first()
                },
                intention = userPreferences.currentIntention.first()
            )
        }

        // If the user disabled this reminder between scheduling and firing,
        // stop the cycle cleanly: do not notify, do not reschedule.
        if (!snapshot.enabled) {
            Log.d(TAG, "${if (isMorning) "Morning" else "Evening"} reminder disabled; skipping.")
            return
        }

        // Self-reschedule the next day's alarm first so a notification-post
        // failure cannot break the daily cadence.
        runCatching {
            if (isMorning) {
                ReminderScheduler.scheduleMorningReminder(context, snapshot.hour, snapshot.minute)
            } else {
                ReminderScheduler.scheduleEveningReminder(context, snapshot.hour, snapshot.minute)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to reschedule ${if (isMorning) "morning" else "evening"} reminder", e)
        }

        postNotification(context, snapshot.intention, isMorning)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun postNotification(context: Context, intention: String, isMorning: Boolean) {
        ReminderScheduler.ensurePracticeReminderChannel(context)

        // Graceful degradation: if the user has disabled notifications for the
        // app (or revoked POST_NOTIFICATIONS on API 33+), skip posting rather
        // than risk a SecurityException from the platform. The reminder has
        // already been rescheduled, so the cadence is preserved.
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled; skipping post (reminder still rescheduled).")
            return
        }

        val notification = buildNotification(context, intention)
        val notificationId = if (isMorning) NOTIFICATION_ID_MORNING else NOTIFICATION_ID_EVENING
        notificationManager.notify(notificationId, notification)
    }

    private fun buildNotification(context: Context, intention: String): android.app.Notification {
        // Tap opens MainActivity via the launcher intent.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent: PendingIntent? = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Devotional, plain body. No exclamation marks, no streak language.
        // If the user set an intention, append it in italics on its own line.
        val body = SpannableStringBuilder("A moment for practice.")
        if (intention.isNotBlank()) {
            body.append("\n")
            val spanStart = body.length
            body.append(intention)
            body.setSpan(
                StyleSpan(Typeface.ITALIC),
                spanStart,
                body.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setContentTitle("Prayer Wheel")
            .setContentText("A moment for practice.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private data class ReminderSnapshot(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val intention: String
    )

    private companion object {
        private const val TAG = "ReminderReceiver"

        // Distinct notification ids so a morning + evening reminder in the same
        // day do not overwrite each other. Distinct from
        // SessionNotificationService.NOTIFICATION_ID (1001).
        private const val NOTIFICATION_ID_MORNING = 1101
        private const val NOTIFICATION_ID_EVENING = 1102

        private const val REQUEST_CODE_OPEN_APP = 2002
    }
}
