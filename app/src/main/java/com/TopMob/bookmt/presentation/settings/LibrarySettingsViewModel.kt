package com.TopMob.bookmt.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.importing.DocumentImport
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibrarySummary(val count: Int, val finished: Int, val reading: Int, val size: Long, val time: Long, val top: List<Book>)

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(repository: BookRepository,
    private val imports: DocumentImport, dispatchers: DispatcherProvider) : ViewModel() {
    val summary = repository.observeBooks().map { books ->
        LibrarySummary(books.size, books.count { it.isFinished }, books.count { it.progress > 0f && !it.isFinished },
            books.sumOf { it.fileSizeBytes }, books.sumOf { it.totalReadingTimeMs },
            books.filter { it.totalReadingTimeMs > 0 }.sortedByDescending { it.totalReadingTimeMs }.take(5))
    }.flowOn(dispatchers.default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val progress = imports.progress
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun dismissError() { mutableError.value = null }
    fun prepareOffline() = viewModelScope.launch {
        try {
            val failures = imports.prepareLibrary()
            if (failures.isNotEmpty()) mutableError.value = failures.joinToString("\n")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableError.value = e.message }
    }
}
