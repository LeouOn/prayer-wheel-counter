package com.prayerwheel.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * [BroadcastReceiver] that listens for session notification action broadcasts
 * (Pause, Resume, Close) and relays them to the registered [SessionNotificationCallback].
 *
 * The [WheelViewModel] sets the [callback] when it starts the notification service,
 * so it can react to user taps on the notification action buttons.
 */
class SessionNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        val cb = callback ?: return

        when (intent.action) {
            SessionNotificationService.ACTION_PAUSE_SESSION -> cb.onPauseSession()
            SessionNotificationService.ACTION_RESUME_SESSION -> cb.onResumeSession()
            SessionNotificationService.ACTION_CLOSE_SESSION -> cb.onCloseSession()
        }
    }

    /**
     * Callback interface that the ViewModel implements to react to
     * notification action button taps.
     */
    interface SessionNotificationCallback {
        fun onPauseSession()
        fun onResumeSession()
        fun onCloseSession()
    }

    companion object {
        /**
         * Static / global reference to the callback.
         * Set by [WheelViewModel] when it creates the notification service.
         */
        var callback: SessionNotificationCallback? = null
    }
}
