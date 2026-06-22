package com.TopMob.bookmt.presentation.bookshelf

import androidx.compose.runtime.Immutable
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookQuery

/** View mode for the library grid. */
enum class BookshelfViewMode { GRID, LIST }

/** Immutable UI state for the bookshelf, rendered by [BookshelfScreen]. */
@Immutable
data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val query: BookQuery = BookQuery(),
    val viewMode: BookshelfViewMode = BookshelfViewMode.GRID,
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && books.isEmpty()
}
