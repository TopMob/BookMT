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
 * @property description Optional user-editable synopsis/notes shown on the book card.
 * @property fileSizeBytes Size of the source file in bytes (0 if unknown).
 * @property totalReadingTimeMs Accumulated time the user has spent reading this book, in millis.
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
    val description: String? = null,
    val fileSizeBytes: Long = 0L,
    val totalReadingTimeMs: Long = 0L,
) {
    /** Progress expressed as a 0..100 integer for UI badges. */
    val progressPercent: Int get() = (progress.coerceIn(0f, 1f) * 100).toInt()

    val hasBeenOpened: Boolean get() = lastReadTimestamp > 0L

    val isFinished: Boolean get() = progress >= 1f

    /**
     * Rough estimate of the book's total character length, inferred from how far the last-read
     * position is into the recorded progress. Null until there's enough signal to estimate.
     */
    val estimatedTotalChars: Int?
        get() = if (progress > 0.01f && lastReadPosition > 0) {
            (lastReadPosition / progress).toInt()
        } else {
            null
        }

    /** Average reading speed in characters per minute, or null if not enough data. */
    val charsPerMinute: Double?
        get() = if (totalReadingTimeMs > 60_000L && lastReadPosition > 0) {
            lastReadPosition / (totalReadingTimeMs / 60_000.0)
        } else {
            null
        }

    /** Estimated milliseconds remaining to finish the book at the current average speed. */
    val estimatedRemainingMs: Long?
        get() {
            val cpm = charsPerMinute ?: return null
            val total = estimatedTotalChars ?: return null
            val remainingChars = (total - lastReadPosition).coerceAtLeast(0)
            if (cpm <= 0.0) return null
            return ((remainingChars / cpm) * 60_000.0).toLong()
        }
}
