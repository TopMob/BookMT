package com.TopMob.bookmt.presentation.bookshelf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the book details/edit screen: observes one book and persists user edits to its card. */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Screen.ARG_BOOK_ID]) {
        "BookDetailsViewModel requires a '${Screen.ARG_BOOK_ID}' argument"
    }

    val book: StateFlow<Book?> = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun save(
        title: String,
        author: String?,
        description: String?,
        coverImagePath: String?,
        tags: List<String>,
    ) {
        viewModelScope.launch {
            repository.updateMetadata(
                bookId = bookId,
                title = title.trim().ifBlank { "Untitled" },
                author = author?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                coverImagePath = coverImagePath,
                tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            )
        }
    }
}
