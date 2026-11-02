package com.prayerwheel.app.notification

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Time budget for receiver work. The system allows roughly 10 seconds after
 * [BroadcastReceiver.goAsync] before it considers the broadcast stuck, so the
 * work is cut off a little before that.
 */
private const val RECEIVER_TIMEOUT_MS = 8_000L

/**
 * Run [block] off the main thread while keeping the broadcast alive.
 *
 * Uses [BroadcastReceiver.goAsync] plus a short-lived IO coroutine bounded by
 * [RECEIVER_TIMEOUT_MS]. The pending result is always finished, even when the
 * block throws or times out, so the receiver can never cause an ANR.
 */
internal fun BroadcastReceiver.runAsync(
    tag: String,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeout(RECEIVER_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            Log.w(tag, "Receiver work timed out", e)
        } catch (e: Exception) {
            Log.e(tag, "Receiver work failed", e)
        } finally {
            pendingResult.finish()
        }
    }
}
