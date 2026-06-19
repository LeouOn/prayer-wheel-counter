package com.prayerwheel.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.prayerwheel.app.MainActivity
import com.prayerwheel.app.PrayerWheelApp
import com.prayerwheel.app.R
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.theme.StarGold
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone

/**
 * Glance home-screen widget: a quiet "today" glance at morning/evening practice
 * status, today's accumulated mantras, and a prominent **Spin** button that
 * deep-links into [MainActivity] in session-start mode.
 *
 * Updates are **event-driven** (see [updateAll] callers in WheelViewModel and the
 * prayer_wheel_widget_info.xml `updatePeriodMillis=0`). There is no periodic
 * polling. Data is loaded once per refresh inside [provideGlance] — never on
 * every composable frame — so the widget never queries the DB while rendering.
 *
 * Tone mirrors the rest of the app: no streaks, no badges, plain numbers.
 */
class PrayerWheelWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadTodaySnapshot(context)
        provideContent {
            GlanceTheme {
                PrayerWheelWidgetContent(snapshot)
            }
        }
    }

    @Composable
    private fun PrayerWheelWidgetContent(snapshot: WidgetSnapshot) {
        val colors = GlanceTheme.colors
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.surface)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = "Prayer Wheel",
                        modifier = GlanceModifier.size(18.dp)
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "Today",
                        style = TextStyle(
                            color = colors.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    )
                }

                Spacer(GlanceModifier.height(8.dp))

                // Slot attribution must stay in sync with TodayProgressCard /
                // WheelViewModel.observeTodayProgress: session hour < reminderEveningHour => morning.
                SlotRow(label = "Morning", done = snapshot.morningCompleted, colors = colors)
                SlotRow(label = "Evening", done = snapshot.eveningCompleted, colors = colors)

                Spacer(GlanceModifier.height(6.dp))

                Text(
                    text = "${NumberFormatter.format(snapshot.todayMantras)} mantras  ·  ${formatPracticeTime(snapshot.todayPracticeMs / 1000L)}",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )

                Spacer(GlanceModifier.height(8.dp))

                Button(
                    text = "Spin",
                    onClick = actionStartActivity<MainActivity>(
                        actionParametersOf(START_SESSION_KEY to true)
                    ),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun SlotRow(
        label: String,
        done: Boolean,
        colors: ColorProviders
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.padding(vertical = 1.dp)
        ) {
            Text(
                text = if (done) "✓" else "○",
                style = TextStyle(
                    color = if (done) ColorProvider(day = StarGold, night = StarGold) else colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = if (done) colors.onSurface else colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (done) FontWeight.Medium else FontWeight.Normal
                )
            )
        }
    }

    /**
     * Loads the today-snapshot once per widget refresh. This is a suspend call
     * running on a Glance-managed dispatcher, not on the composable/main thread,
     * so the DB read does not block rendering nor happen per-frame.
     *
     * Attribution mirrors [com.prayerwheel.app.viewmodel.WheelViewModel]'s
     * `observeTodayProgress` exactly to keep widget and in-app card consistent.
     */
    private suspend fun loadTodaySnapshot(context: Context): WidgetSnapshot {
        val app = context.applicationContext as PrayerWheelApp
        val (startOfDay, startOfNextDay) = currentDayWindow()

        val eveningHour = runCatching {
            app.userPreferences.reminderEveningHour.first()
        }.getOrDefault(19)

        val sessions = runCatching {
            app.database.sessionDao()
                .getSessionsBetween(startOfDay, startOfNextDay)
                .first()
        }.getOrDefault(emptyList())

        var morning = false
        var evening = false
        var mantras = BigInteger.ZERO
        var practiceMs = 0L
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        for (session in sessions) {
            calendar.timeInMillis = session.startedAt
            if (calendar.get(Calendar.HOUR_OF_DAY) < eveningHour) {
                morning = true
            } else {
                evening = true
            }
            mantras = mantras.add(session.totalMantras)
            val endMs = session.endedAt ?: session.startedAt
            if (endMs >= session.startedAt) {
                practiceMs += (endMs - session.startedAt)
            }
        }

        return WidgetSnapshot(
            morningCompleted = morning,
            eveningCompleted = evening,
            todayMantras = mantras,
            todayPracticeMs = practiceMs
        )
    }

    private fun currentDayWindow(): Pair<Long, Long> {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val startOfNextDay = calendar.timeInMillis
        return startOfDay to startOfNextDay
    }

    private fun formatPracticeTime(seconds: Long): String {
        if (seconds <= 0L) return "0m"
        val totalMinutes = seconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
    }

    internal data class WidgetSnapshot(
        val morningCompleted: Boolean,
        val eveningCompleted: Boolean,
        val todayMantras: BigInteger,
        val todayPracticeMs: Long
    )

    companion object {
        /**
         * ActionParameters key carrying the "launch and start a session" flag.
         * Glance serializes ActionParameters into the launched Intent's extras,
         * so MainActivity reads this via `intent.getBooleanExtra("start_session", false)`.
         */
        val START_SESSION_KEY: ActionParameters.Key<Boolean> =
            ActionParameters.Key("start_session")
    }
}
