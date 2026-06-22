package com.TopMob.bookmt.domain.model

/**
 * A user annotation anchored to a position in a book. Covers the three Moon+ Reader style
 * interactions — plain bookmarks, highlights, and notes — distinguished by [type].
 *
 * @property position Absolute character offset into the book's flattened text content.
 * @property endPosition Exclusive end offset for highlights/notes spanning a range; equals
 *           [position] for a point bookmark.
 * @property textSnippet The selected/anchoring text, stored so it survives re-pagination.
 * @property note Optional user note text.
 * @property colorArgb Highlight color (packed ARGB); null for plain bookmarks.
 */
data class Bookmark(
    val id: Long = 0L,
    val bookId: Long,
    val type: BookmarkType,
    val position: Int,
    val endPosition: Int = position,
    val textSnippet: String = "",
    val note: String? = null,
    val colorArgb: Int? = null,
    val chapterTitle: String? = null,
    val timestamp: Long = 0L,
)

enum class BookmarkType { BOOKMARK, HIGHLIGHT, NOTE }
