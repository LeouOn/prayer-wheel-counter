package com.prayerwheel.app.ui.wheel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The current local date, kept fresh while the screen is shown.
 *
 * Re-reads the date whenever the screen resumes (covers returning to the app
 * the next morning, or a time-zone change) and again just after each local
 * midnight while the screen stays open (covers Dec 31 rolling into Jan 1).
 */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now()
    }

    LaunchedEffect(Unit) {
        while (true) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            val waitMs = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0L) + 1_000L
            delay(waitMs)
            today = LocalDate.now()
        }
    }

    return today
}
