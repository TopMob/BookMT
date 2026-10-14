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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Backs the book details/edit screen: observes one book and persists user edits to its card. */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Screen.ARG_BOOK_ID]) {
        "BookDetailsViewModel requires a '${Screen.ARG_BOOK_ID}' argument"
    }

    val book: StateFlow<Book?> = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun importCover(uri: android.net.Uri, onImported: (String) -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(context.filesDir, "covers").apply { mkdirs() }
                    val target = java.io.File(dir, "cover_${bookId}_${java.util.UUID.randomUUID()}.img")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 20L * 1024 * 1024) { "Cover exceeds 20 MB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: error("Cannot open cover")
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(target.path, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image file" }
                        target.absolutePath
                    } catch (e: Exception) { target.delete(); throw e }
                }
                onImported(path)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "Cannot import cover" }
            finally { _isSaving.value = false }
        }
    }

    fun save(
        title: String,
        author: String?,
        description: String?,
        coverImagePath: String?,
        tags: List<String>,
        onSaved: () -> Unit,
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
            repository.updateMetadata(
                bookId = bookId,
                title = title.trim().ifBlank { "Untitled" },
                author = author?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                coverImagePath = coverImagePath,
                tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            )
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save book details"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
