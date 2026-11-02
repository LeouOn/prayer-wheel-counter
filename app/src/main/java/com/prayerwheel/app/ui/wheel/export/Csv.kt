package com.prayerwheel.app.ui.wheel.export

/** RFC 4180 record separator. */
internal const val CSV_EOL = "\r\n"

/**
 * Quotes one CSV field per RFC 4180: always wrapped in double quotes, with
 * embedded quotes doubled. Commas, CR and LF are then safe inside the field.
 */
internal fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** Joins already-formatted fields into one record (no trailing line break). */
internal fun csvRow(fields: List<String>): String = fields.joinToString(",")
