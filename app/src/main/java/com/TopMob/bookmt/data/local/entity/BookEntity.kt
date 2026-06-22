package com.TopMob.bookmt.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room representation of a library book. Indexed on the columns the bookshelf sorts/filters by so
 * large libraries stay responsive. Tags are stored as a delimited string via a type converter.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["last_read_timestamp"]),
        Index(value = ["added_timestamp"]),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "format")
    val format: String,

    @ColumnInfo(name = "cover_image_path")
    val coverImagePath: String?,

    @ColumnInfo(name = "progress_percent")
    val progressPercent: Float,

    @ColumnInfo(name = "last_read_position")
    val lastReadPosition: Int,

    @ColumnInfo(name = "last_read_timestamp")
    val lastReadTimestamp: Long,

    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long,

    @ColumnInfo(name = "tags")
    val tags: List<String>,
)
