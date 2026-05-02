package com.prayerwheel.app.data.db

import androidx.room.TypeConverter
import java.math.BigInteger

/**
 * Room TypeConverters for types not natively supported by SQLite.
 *
 * BigInteger is stored as TEXT to support arbitrarily large mantra counts
 * at stupa-class capacity (trillions and beyond).
 */
class Converters {

    @TypeConverter
    fun fromBigInteger(value: BigInteger?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toBigInteger(value: String?): BigInteger? {
        return value?.let { BigInteger(it) }
    }
}
