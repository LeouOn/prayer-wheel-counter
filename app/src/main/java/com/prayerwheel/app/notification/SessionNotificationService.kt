package com.prayerwheel.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigInteger


/**
 * Android foreground service that shows a persistent notification
 * during active prayer wheel sessions.
 *
 * The service should NOT be instantiated directly — use the static [start], [stop],
 * and [update] companion methods instead. The ViewModel calls these companion
 * methods which use [Context.startForegroundService] / [Context.stopService] to
 * manage the service lifecycle.
 *
 * Displays elapsed time (HH:MM:SS) and an abbreviated mantra count (e.g., "12.3K mantras").
 * Provides Pause/Resume and Close Session action buttons via broadcast intents.
 */
class SessionNotificationService : Service() {

    // ── Coroutine scope & timer job ──────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    // ── Session state (also mirrored in companion for stateless updates) ─────
    private var sessionDurationSeconds: Long = 0L
    private var mantraCountRaw: BigInteger = BigInteger.ZERO
    private var isPaused: Boolean = false

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        companionInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_SESSION -> handlePause()
            ACTION_RESUME_SESSION -> handleResume()
            ACTION_CLOSE_SESSION -> handleClose()
            else -> {
                // Extract initial extras if present
                if (intent != null) {
                    sessionDurationSeconds = intent.getLongExtra(EXTRA_SESSION_DURATION_SECONDS, 0L)
                    val rawStr = intent.getStringExtra(EXTRA_MANTRA_COUNT_RAW)
                    if (rawStr != null) {
                        mantraCountRaw = BigInteger(rawStr)
                    }
                    isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                }
                // Write initial state to companion so update() sees it
                companionDurationSeconds = sessionDurationSeconds
                companionMantraCount = mantraCountRaw
                companionPaused = isPaused

                startTimerLoop()
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(sessionDurationSeconds, mantraCountRaw, isPaused)
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        companionInstance = null
        stopTimerLoop()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Action handlers ──────────────────────────────────────────────────────

    private fun handlePause() {
        isPaused = true
        companionPaused = true
        companionDurationSeconds = sessionDurationSeconds
        stopTimerLoop()
        updateNotification()
    }

    private fun handleResume() {
        isPaused = false
        companionPaused = false
        startTimerLoop()
        updateNotification()
    }

    private fun handleClose() {
        stopTimerLoop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Timer loop ───────────────────────────────────────────────────────────

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (!isPaused) {
                    sessionDurationSeconds++
                    companionDurationSeconds = sessionDurationSeconds
                    updateNotification()
                }
            }
        }
    }

    private fun stopTimerLoop() {
        timerJob?.cancel()
        timerJob = null
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun updateNotification() {
        companionMantraCount = mantraCountRaw
        val notification = buildNotification(sessionDurationSeconds, mantraCountRaw, isPaused)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        durationSeconds: Long,
        mantraCount: BigInteger,
        paused: Boolean
    ): Notification {
        val formattedTime = formatElapsedTime(durationSeconds)
        val formattedMantras = formatMantraCount(mantraCount)
        val contentText = "$formattedTime  \u2022  $formattedMantras mantras"

        // Content intent — tapping the notification body opens MainActivity
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = openIntent?.let {
            PendingIntent.getActivity(
                this, REQUEST_CODE_OPEN_APP, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Pause / Resume action (broadcast-based)
        val pauseResumeAction: NotificationCompat.Action = if (paused) {
            NotificationCompat.Action.Builder(
                0 /* no icon */,
                "Resume",
                createPendingIntent(this, ACTION_RESUME_SESSION)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                0 /* no icon */,
                "Pause",
                createPendingIntent(this, ACTION_PAUSE_SESSION)
            ).build()
        }

        // Close action (broadcast-based)
        val closeAction = NotificationCompat.Action.Builder(
            0 /* no icon */,
            "Close Session",
            createPendingIntent(this, ACTION_CLOSE_SESSION)
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Prayer Wheel Session")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPending)
            .addAction(pauseResumeAction)
            .addAction(closeAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ── Companion: static API + mirrored state for cross-process updates ─────

    companion object {
        // ── Notification channel ─────────────────────────────────────────────
        const val CHANNEL_ID = "session_notification_channel"
        const val CHANNEL_NAME = "Session"
        const val CHANNEL_DESCRIPTION = "Shows ongoing prayer wheel session information"

        // ── Notification ID ──────────────────────────────────────────────────
        const val NOTIFICATION_ID = 1001

        // ── Broadcast actions ────────────────────────────────────────────────
        const val ACTION_PAUSE_SESSION = "com.prayerwheel.app.ACTION_PAUSE_SESSION"
        const val ACTION_RESUME_SESSION = "com.prayerwheel.app.ACTION_RESUME_SESSION"
        const val ACTION_CLOSE_SESSION = "com.prayerwheel.app.ACTION_CLOSE_SESSION"

        // ── Extra keys ───────────────────────────────────────────────────────
        const val EXTRA_SESSION_MANTRAS = "session_mantras"
        const val EXTRA_SESSION_DURATION_SECONDS = "session_duration_seconds"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_MANTRA_COUNT_RAW = "mantra_count_raw"

        // ── PendingIntent request codes ──────────────────────────────────────
        private const val REQUEST_CODE_OPEN_APP = 2000
        private const val REQUEST_CODE_PAUSE = 2001
        private const val REQUEST_CODE_RESUME = 2002
        private const val REQUEST_CODE_CLOSE = 2003

        // ── Mirrored companion state (written by service, read by static update) ──
        @Volatile
        var companionDurationSeconds: Long = 0L
            private set
        @Volatile
        var companionMantraCount: BigInteger = BigInteger.ZERO
            private set
        @Volatile
        var companionPaused: Boolean = false
            private set

        // ── Track the running service instance for same-process updates ─────
        @Volatile
        private var companionInstance: SessionNotificationService? = null

        // ── Track whether service is running ─────────────────────────────────
        @Volatile
        private var serviceRunning: Boolean = false

        /**
         * Starts the foreground service with initial session values.
         * Safe to call from anywhere; uses [Context.startForegroundService] on API 26+.
         */
        fun start(context: Context, durationSeconds: Long, mantraCount: BigInteger, paused: Boolean = false) {
            companionDurationSeconds = durationSeconds
            companionMantraCount = mantraCount
            companionPaused = paused
            serviceRunning = true

            val intent = Intent(context, SessionNotificationService::class.java).apply {
                putExtra(EXTRA_SESSION_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_MANTRA_COUNT_RAW, mantraCount.toString())
                putExtra(EXTRA_IS_PAUSED, paused)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the foreground service.
         */
        fun stop(context: Context) {
            serviceRunning = false
            companionDurationSeconds = 0L
            companionMantraCount = BigInteger.ZERO
            companionPaused = false
            context.stopService(Intent(context, SessionNotificationService::class.java))
        }

        /**
         * Updates the notification with latest session values.
         *
         * Since the service may be running in the same process, we store the values
         * in companion fields AND send a broadcast to the service to refresh its
         * notification. The service's own timer loop is authoritative for time,
         * but this allows mantra-count updates to reach the notification immediately.
         */
        fun update(durationSeconds: Long, mantraCount: BigInteger, paused: Boolean) {
            companionDurationSeconds = durationSeconds
            companionMantraCount = mantraCount
            companionPaused = paused

            if (!serviceRunning) return

            // Push the values to the running service instance and refresh notification
            val instance = companionInstance
            if (instance != null) {
                instance.sessionDurationSeconds = durationSeconds
                instance.mantraCountRaw = mantraCount
                instance.isPaused = paused
                instance.updateNotification()
            }
        }

        // ── PendingIntent factory ───────────────────────────────────────────

        /**
         * Creates a broadcast [PendingIntent] for the given action string.
         * The [SessionNotificationReceiver] will pick this up and relay to the ViewModel.
         */
        fun createPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                setClass(context, SessionNotificationReceiver::class.java)
            }
            val requestCode = when (action) {
                ACTION_PAUSE_SESSION -> REQUEST_CODE_PAUSE
                ACTION_RESUME_SESSION -> REQUEST_CODE_RESUME
                ACTION_CLOSE_SESSION -> REQUEST_CODE_CLOSE
                else -> 0
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // ── Time formatting ──────────────────────────────────────────────────

        /**
         * Formats elapsed seconds into HH:MM:SS format.
         */
        fun formatElapsedTime(totalSeconds: Long): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        // ── Mantra count formatting ──────────────────────────────────────────

        /**
         * Formats a [BigInteger] mantra count into a compact human-readable string.
         *
         * - Under 1000: exact number (e.g., "842")
         * - 1K – 999,999: "X.XK" (e.g., "12.3K")
         * - 1M – 999,999,999: "X.XM" (e.g., "1.2M")
         * - 1B – 999,999,999,999: "X.XB" (e.g., "4.5B")
         * - 1T+ : "X.XT" (e.g., "3.1T")
         */
        fun formatMantraCount(count: BigInteger): String {
            if (count < BigInteger.valueOf(1000)) {
                return count.toString()
            }

            val magnitude = count.toString().length - 1
            val (divisor, suffix) = when {
                magnitude < 6 -> BigInteger.valueOf(1000) to "K"
                magnitude < 9 -> BigInteger.valueOf(1_000_000) to "M"
                magnitude < 12 -> BigInteger.valueOf(1_000_000_000) to "B"
                else -> BigInteger.valueOf(1_000_000_000_000) to "T"
            }

            val scaled = count * BigInteger.TEN
            val quotient = scaled / divisor
            val whole = quotient / BigInteger.TEN
            val fraction = quotient % BigInteger.TEN

            return "$whole.$fraction$suffix"
        }
    }
}
