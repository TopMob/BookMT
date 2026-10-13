package com.TopMob.bookmt.presentation.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.usecase.GetBooksUseCase
import com.TopMob.bookmt.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val libraryPreferences: com.TopMob.bookmt.data.settings.LibraryPreferences,
    private val documentImport: com.TopMob.bookmt.data.importing.DocumentImport,
) : ViewModel() {

    private val query = MutableStateFlow(libraryPreferences.query())
    private val viewMode = MutableStateFlow(libraryPreferences.viewMode())
    private val transientError = MutableStateFlow<String?>(null)

    private val booksFlow = query
        .flatMapLatest { q -> getBooks(q) }

    val uiState: StateFlow<BookshelfUiState> = combine(
        booksFlow,
        query,
        viewMode,
        transientError,
        bookRepository.observeBooks(),
    ) { books, q, mode, error, allBooks ->
        BookshelfUiState(
            books = books,
            allBooks = allBooks,
            continueReadingBook = allBooks.filter { it.hasBeenOpened && it.progress < 1f }
                .maxByOrNull { it.lastReadTimestamp },
            query = q,
            viewMode = mode,
            availableTags = allBooks.flatMap { it.tags }.distinct().sorted(),
            isLoading = false,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BookshelfUiState(),
    )

    init {
        viewModelScope.launch { query.collect { libraryPreferences.saveQuery(it) } }
        viewModelScope.launch { viewMode.collect { libraryPreferences.saveViewMode(it) } }
    }
    val importProgress = documentImport.progress
    fun onImportDocuments(uris: List<android.net.Uri>) = viewModelScope.launch {
        val failures = documentImport.import(uris).failures
        if (failures.isNotEmpty()) transientError.value = failures.joinToString("\n")
    }
    fun onStatusFilter(status: com.TopMob.bookmt.domain.model.LibraryStatus) = query.update { it.copy(status = status) }
    fun onFormatFilter(format: com.TopMob.bookmt.domain.model.BookFormat?) = query.update { it.copy(format = format) }
    fun onReverseSort() = query.update { it.copy(ascending = !it.ascending) }
    fun onResetFilters() = query.update { it.copy(tags = emptySet(), status = com.TopMob.bookmt.domain.model.LibraryStatus.ALL, format = null, searchQuery = "", onlyInProgress = false) }
    fun onToggleFavorite(book: Book) = libraryAction {
        bookRepository.updateTags(book.id, if ("Favorites" in book.tags) book.tags - "Favorites" else book.tags + "Favorites")
    }

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
        libraryAction { bookRepository.deleteBook(bookId) }
    }

    /**
     * Toggles a book's read state. The library has no dedicated "read" flag, so read is modeled as
     * full progress (1f) and unread as zero progress reset to the start of the book.
     */
    fun onSetReadState(book: Book, read: Boolean) {
        libraryAction { applyReadState(book, read) }
    }

    /* -------- Batch (multi-select) operations -------- */

    fun onDeleteBooks(ids: Collection<Long>) {
        libraryAction { ids.forEach { bookRepository.deleteBook(it) } }
    }

    fun onSetReadStateForBooks(books: Collection<Book>, read: Boolean) {
        libraryAction { books.forEach { applyReadState(it, read) } }
    }

    fun onAddTagToBooks(books: Collection<Book>, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        libraryAction {
            books.forEach { book ->
                if (trimmed !in book.tags) bookRepository.updateTags(book.id, book.tags + trimmed)
            }
        }
    }

    private suspend fun applyReadState(book: Book, read: Boolean) {
        if (read) {
            bookRepository.updateProgress(book.id, progress = 1f, position = book.lastReadPosition)
        } else {
            bookRepository.updateProgress(book.id, progress = 0f, position = 0)
        }
    }

    private fun libraryAction(action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            transientError.value = e.message ?: "Failed to update library"
        }
    }

    fun onErrorShown() {
        transientError.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
