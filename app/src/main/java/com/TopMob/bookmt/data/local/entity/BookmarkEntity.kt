package com.TopMob.bookmt.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room representation of a bookmark / highlight / note. A foreign key on [bookId] with cascade
 * delete keeps annotations from outliving their book.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "end_position")
    val endPosition: Int,

    @ColumnInfo(name = "text_snippet")
    val textSnippet: String,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "color_argb")
    val colorArgb: Int?,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)
