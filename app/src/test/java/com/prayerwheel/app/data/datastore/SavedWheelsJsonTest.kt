package com.prayerwheel.app.data.datastore

import com.prayerwheel.app.data.model.SavedWheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWheelsJsonTest {

    private val a = SavedWheel("a", "Home", 1_000_000L, "om", "traditional_gold", 1L)
    private val b = SavedWheel("b", "Stupa", 1_000_000_000_000L, "tara", "lotus", 2L)

    @Test
    fun roundTrip() {
        val json = SavedWheelsJson.savedWheelsToJson(listOf(a, b))
        assertEquals(listOf(a, b), SavedWheelsJson.parseSavedWheelsJson(json))
    }

    @Test
    fun malformedEntry_isSkipped_othersKept() {
        val json = """[
            {"id":"a","name":"Home","capacity":1000000,"mantraId":"om","skinId":"traditional_gold","createdAt":1},
            {"id":"broken","name":"No capacity","mantraId":"om"},
            "not an object",
            {"id":"b","name":"Stupa","capacity":1000000000000,"mantraId":"tara","skinId":"lotus","createdAt":2}
        ]"""
        assertEquals(listOf(a, b), SavedWheelsJson.parseSavedWheelsJson(json))
    }

    @Test
    fun optionalFieldsDefault() {
        val parsed = SavedWheelsJson.parseSavedWheelsJson("""[{"id":"x","name":"X","capacity":5,"mantraId":"om"}]""")
        assertEquals("traditional_gold", parsed.single().skinId)
    }

    @Test
    fun unreadableArray_isEmpty() {
        assertTrue(SavedWheelsJson.parseSavedWheelsJson("{not json").isEmpty())
        assertTrue(SavedWheelsJson.parseSavedWheelsJson("").isEmpty())
    }
}
