package com.prayerwheel.app.ui.wheel.export

import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.calculateDuration
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

internal fun buildCsvContent(sessions: List<Session>): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return buildString {
        append(
            csvRow(
                listOf(
                    "Date", "Duration", "Mantra", "Rotations", "Mantras", "Avg RPM", "Peak RPM",
                    "Mode", "Intention", "Dedication", "Wheel", "Label"
                )
            )
        )
        append(CSV_EOL)
        sessions.forEach { session ->
            val displayName = Mantras.byId(session.mantraId)?.displayName ?: session.mantraId
            val duration = session.endedAt?.let { calculateDuration(session.startedAt, it) } ?: ""
            append(
                csvRow(
                    listOf(
                        csvField(dateFormat.format(Date(session.startedAt))),
                        csvField(duration),
                        csvField(displayName),
                        session.rotationCount.toString(),
                        session.totalMantras.toString(),
                        session.averageRpm.toString(),
                        session.peakRpm.toString(),
                        csvField(session.mode),
                        csvField(session.intention ?: ""),
                        csvField(session.dedication ?: ""),
                        csvField(session.wheelId ?: ""),
                        csvField(session.label ?: "")
                    )
                )
            )
            append(CSV_EOL)
        }
    }
}

/**
 * Builds the JSON backup with org.json so every string (intention,
 * dedication, label) is escaped correctly, including backslashes, newlines
 * and control characters. `exportedAt` is a true UTC ISO-8601 instant.
 */
internal fun buildJsonContent(sessions: List<Session>, exportedAt: Instant = Instant.now()): String {
    val array = JSONArray()
    sessions.forEach { session ->
        array.put(
            JSONObject()
                .put("id", session.id)
                .put("startedAt", session.startedAt)
                .put("endedAt", session.endedAt ?: JSONObject.NULL)
                .put("rotationCount", session.rotationCount)
                .put("mantrasPerRotation", session.mantrasPerRotation)
                .put("totalMantras", jsonBigInteger(session.totalMantras))
                .put("mantraId", session.mantraId)
                .put("mode", session.mode)
                .put("averageRpm", jsonFloat(session.averageRpm))
                .put("peakRpm", jsonFloat(session.peakRpm))
                .put("totalSpins", session.totalSpins)
                .put("wheelId", session.wheelId ?: JSONObject.NULL)
                .put("intention", session.intention ?: JSONObject.NULL)
                .put("dedication", session.dedication ?: JSONObject.NULL)
                .put("label", session.label ?: JSONObject.NULL)
                .put("sessionGoal", session.sessionGoal ?: JSONObject.NULL)
        )
    }
    return JSONObject()
        .put("exportedAt", exportedAt.toString())
        .put("sessionCount", sessions.size)
        .put("sessions", array)
        .toString(2)
}

/**
 * Lifetime counts are BigInteger and are written as exact JSON numbers.
 * org.json validates numbers through a double, which overflows past ~1e308,
 * so only those astronomically large values fall back to a decimal string.
 */
private fun jsonBigInteger(value: BigInteger): Any =
    if (value.bitLength() < 1000) value else value.toString()

/** JSON has no NaN/Infinity; write null instead of failing the whole export. */
private fun jsonFloat(value: Float): Any = if (value.isFinite()) value else JSONObject.NULL
