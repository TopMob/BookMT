package com.TopMob.bookmt.domain.model

/**
 * A book as the rest of the app reasons about it (pure domain type — no Room/Android dependencies).
 *
 * @property id Stable local identifier (Room primary key).
 * @property uri Content URI / file path the book was imported from.
 * @property title Display title (from metadata, falling back to file name).
 * @property author Author, or null if unknown.
 * @property format Parsed container format.
 * @property coverImagePath Local cached cover path, or null.
 * @property progress Reading progress in the range 0f..1f.
 * @property lastReadTimestamp Epoch millis of last open; 0 if never opened.
 * @property addedTimestamp Epoch millis when imported.
 * @property tags User-assigned custom tags used for filtering.
 * @property lastReadPosition Absolute character offset into the book's flattened text.
 */
data class Book(
    val id: Long = 0L,
    val uri: String,
    val title: String,
    val author: String? = null,
    val format: BookFormat = BookFormat.UNKNOWN,
    val coverImagePath: String? = null,
    val progress: Float = 0f,
    val lastReadTimestamp: Long = 0L,
    val addedTimestamp: Long = 0L,
    val tags: List<String> = emptyList(),
    val lastReadPosition: Int = 0,
) {
    /** Progress expressed as a 0..100 integer for UI badges. */
    val progressPercent: Int get() = (progress.coerceIn(0f, 1f) * 100).toInt()

    val hasBeenOpened: Boolean get() = lastReadTimestamp > 0L
}
