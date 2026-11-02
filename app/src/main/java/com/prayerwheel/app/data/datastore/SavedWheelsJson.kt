package com.prayerwheel.app.data.datastore

import com.prayerwheel.app.data.model.SavedWheel
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON (de)serialization for the saved-wheels preference.
 */
internal object SavedWheelsJson {

    /**
     * Parses the saved-wheels array entry by entry: a malformed entry is
     * skipped on its own. (Previously one bad entry made the whole list parse
     * as empty, and the next add/update/delete persisted that loss of every
     * saved wheel.) Returns an empty list only if the array itself is unreadable.
     */
    fun parseSavedWheelsJson(jsonString: String): List<SavedWheel> {
        val jsonArray = try {
            JSONArray(jsonString)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val obj = jsonArray.getJSONObject(i)
                SavedWheel(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    capacity = obj.getLong("capacity"),
                    mantraId = obj.getString("mantraId"),
                    skinId = obj.optString("skinId", "traditional_gold"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun savedWheelsToJson(wheels: List<SavedWheel>): String {
        val jsonArray = JSONArray()
        wheels.forEach { wheel ->
            val obj = JSONObject().apply {
                put("id", wheel.id)
                put("name", wheel.name)
                put("capacity", wheel.capacity)
                put("mantraId", wheel.mantraId)
                put("skinId", wheel.skinId)
                put("createdAt", wheel.createdAt)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
