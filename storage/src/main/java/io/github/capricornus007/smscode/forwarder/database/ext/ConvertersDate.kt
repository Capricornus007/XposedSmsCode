package io.github.capricornus007.smscode.forwarder.database.ext

import androidx.room.TypeConverter
import java.util.Date

class ConvertersDate {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}