package com.TopMob.bookmt.presentation.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.usecase.GetBooksUseCase
import com.TopMob.bookmt.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the bookshelf. Holds the active [BookQuery] and view mode as inputs, and exposes a single
 * [uiState] derived reactively from the book repository — re-querying automatically whenever either
 * the data or the query changes. Heavy filtering/sorting happens in [GetBooksUseCase] off the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val getBooks: GetBooksUseCase,
    private val importBook: ImportBookUseCase,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val query = MutableStateFlow(BookQuery())
    private val viewMode = MutableStateFlow(BookshelfViewMode.GRID)
    private val transientError = MutableStateFlow<String?>(null)

    private val booksFlow = query
        .distinctUntilChanged()
        .flatMapLatest { q -> getBooks(q) }

    val uiState: StateFlow<BookshelfUiState> = combine(
        booksFlow,
        query,
        viewMode,
        transientError,
    ) { books, q, mode, error ->
        BookshelfUiState(
            books = books,
            query = q,
            viewMode = mode,
            availableTags = books.flatMap { it.tags }.distinct().sorted(),
            isLoading = false,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BookshelfUiState(),
    )

    fun onSortOrderChange(order: BookSortOrder) = query.update { current ->
        // Title/author default to A→Z, time/progress to descending (newest / most-progressed first).
        val ascending = order == BookSortOrder.TITLE || order == BookSortOrder.AUTHOR
        current.copy(sortOrder = order, ascending = ascending)
    }

    fun onSearchChange(text: String) = query.update { it.copy(searchQuery = text) }

    fun onToggleOnlyInProgress() = query.update { it.copy(onlyInProgress = !it.onlyInProgress) }

    fun onTagFilterChange(tags: Set<String>) = query.update { it.copy(tags = tags) }

    fun onToggleViewMode() = viewMode.update {
        if (it == BookshelfViewMode.GRID) BookshelfViewMode.LIST else BookshelfViewMode.GRID
    }

    fun onImportBook(uri: String, fileName: String, mimeType: String?) {
        viewModelScope.launch {
            when (val result = importBook(uri, fileName, mimeType)) {
                is Resource.Error -> transientError.value =
                    result.message ?: "Failed to import book"
                else -> transientError.value = null
            }
        }
    }

    fun onDeleteBook(bookId: Long) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }

    fun onErrorShown() {
        transientError.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
