package com.TopMob.bookmt.data.local.converter

import androidx.room.TypeConverter

/** Room type converters for non-primitive columns. */
class Converters {

    /**
     * Tags are stored as a single newline-delimited string. Newline is chosen as the delimiter
     * because tags may legitimately contain commas/spaces, and it cannot appear inside a tag.
     */
    @TypeConverter
    fun fromTagList(tags: List<String>): String =
        tags.filter { it.isNotBlank() }.joinToString(TAG_DELIMITER)

    @TypeConverter
    fun toTagList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(TAG_DELIMITER).filter { it.isNotBlank() }

    private companion object {
        const val TAG_DELIMITER = "\n"
    }
}
