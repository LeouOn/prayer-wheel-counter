package com.prayerwheel.app.ui.wheel.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun plainValue_isQuoted() {
        assertEquals("\"Om mani padme hum\"", csvField("Om mani padme hum"))
    }

    @Test
    fun embeddedQuotes_areDoubled() {
        assertEquals("\"for \"\"all\"\" beings\"", csvField("for \"all\" beings"))
    }

    @Test
    fun commasAndNewlines_stayInsideQuotes() {
        assertEquals("\"a, b\nc\r\nd\"", csvField("a, b\nc\r\nd"))
    }

    @Test
    fun emptyValue_isEmptyQuotedField() {
        assertEquals("\"\"", csvField(""))
    }

    @Test
    fun row_joinsWithCommas_andParsesBack() {
        val row = csvRow(listOf(csvField("x,\"y\""), "42", csvField("line1\nline2")))
        assertEquals("\"x,\"\"y\"\"\",42,\"line1\nline2\"", row)
        assertEquals(listOf("x,\"y\"", "42", "line1\nline2"), parseRfc4180Record(row))
    }

    @Test
    fun recordSeparator_isCrlf() {
        assertEquals("\r\n", CSV_EOL)
    }

    /** Minimal RFC 4180 parser for a single record, used to check round-tripping. */
    private fun parseRfc4180Record(record: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < record.length) {
            val c = record[i]
            when {
                inQuotes && c == '"' && i + 1 < record.length && record[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { fields.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}
