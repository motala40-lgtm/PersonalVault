package com.example.personalvault.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEntryType(type: EntryType): String = type.name

    @TypeConverter
    fun toEntryType(value: String): EntryType = EntryType.valueOf(value)

    @TypeConverter
    fun fromRepeatType(type: RepeatType): String = type.name

    @TypeConverter
    fun toRepeatType(value: String): RepeatType = RepeatType.valueOf(value)
}
