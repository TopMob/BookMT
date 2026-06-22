package com.TopMob.bookmt.domain.model

/** Sort orders offered on the bookshelf. */
enum class BookSortOrder {
    RECENTLY_ADDED,
    RECENTLY_READ,
    TITLE,
    AUTHOR,
    PROGRESS,
}

/**
 * Declarative filter/sort spec applied to the library. Kept as a pure value so it can live in
 * UI state, be persisted, and be unit-tested independently of the data source.
 *
 * @property searchQuery Case-insensitive match against title and author.
 * @property tags Books must contain *all* of these tags (empty = no tag filter).
 * @property onlyInProgress When true, hides finished (==100%) and untouched (==0%) books.
 */
data class BookQuery(
    val sortOrder: BookSortOrder = BookSortOrder.RECENTLY_ADDED,
    val ascending: Boolean = false,
    val searchQuery: String = "",
    val tags: Set<String> = emptySet(),
    val onlyInProgress: Boolean = false,
)
