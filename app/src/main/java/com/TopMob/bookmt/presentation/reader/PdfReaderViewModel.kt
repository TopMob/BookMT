package com.TopMob.bookmt.presentation.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.usecase.ManageBookmarksUseCase
import com.TopMob.bookmt.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

data class PdfReaderState(val book: Book? = null, val page: Int = 0, val count: Int = 0,
    val bitmap: Bitmap? = null, val loading: Boolean = true, val error: String? = null,
    val bookmarks: List<Bookmark> = emptyList())

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val bookmarks: ManageBookmarksUseCase,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState[Screen.ARG_BOOK_ID])
    private val initialOffset: Int = savedState[Screen.ARG_OFFSET] ?: -1
    private val mutable = MutableStateFlow(PdfReaderState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var temporary: File? = null
    private var renderJob: Job? = null
    private var sessionStart: Long? = null

    init {
        viewModelScope.launch { bookmarks.observe(id).collect { entries -> mutable.update { it.copy(bookmarks = entries) } } }
        viewModelScope.launch {
            try {
                val book = repository.prepareOffline(repository.getBook(id) ?: error("Book not found"))
                val count = withContext(Dispatchers.IO) {
                    mutex.withLock {
                        // SAF providers may return pipes; PdfRenderer requires a seekable descriptor.
                        val file = File.createTempFile("reader-", ".pdf", context.cacheDir)
                        temporary = file
                        context.contentResolver.openInputStream(Uri.parse(book.uri))?.use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        } ?: error("Cannot open PDF")
                        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val pdf = try { PdfRenderer(descriptor) } catch (e: Exception) { descriptor.close(); throw e }
                        renderer = pdf
                        pdf.pageCount
                    }
                }
                require(count > 0) { "PDF has no pages" }
                mutable.update { it.copy(book = book, count = count) }
                goTo(initialOffset.takeIf { it >= 0 } ?: book.lastReadPosition)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun goTo(index: Int) {
        if (mutable.value.count == 0) return
        val page = index.coerceIn(0, mutable.value.count - 1)
        renderJob?.cancel()
        mutable.update { it.copy(page = page, loading = true, bitmap = null, error = null) }
        renderJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    mutex.withLock {
                        val pdf = renderer ?: error("PDF closed")
                        pdf.openPage(page).use { source ->
                            // Bound bitmap memory, including unusually tall or wide pages.
                            val scale = minOf(2f, 2048f / maxOf(source.width, source.height))
                            val bitmap = Bitmap.createBitmap((source.width * scale).toInt().coerceAtLeast(1),
                                (source.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            } catch (e: Exception) { bitmap.recycle(); throw e }
                        }
                    }
                }
                ensureActive()
                mutable.update { it.copy(bitmap = bitmap, loading = false) }
                repository.updateProgress(id, (page + 1f) / mutable.value.count, page)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun addBookmark(pageLabel: String) = action {
        val s = mutable.value
        if (s.count > 0 && s.bookmarks.none { it.position == s.page }) bookmarks.add(
            Bookmark(bookId = id, type = BookmarkType.BOOKMARK, position = s.page, textSnippet = pageLabel))
    }
    fun deleteBookmark(id: Long) = action { bookmarks.delete(id) }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }
    fun resume() { if (sessionStart == null) sessionStart = SystemClock.elapsedRealtime() }
    fun pause() {
        val start = sessionStart ?: return
        sessionStart = null
        viewModelScope.launch(NonCancellable) {
            try { repository.addReadingTime(id, SystemClock.elapsedRealtime() - start) }
            catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
        }
    }
    override fun onCleared() {
        pause()
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            mutex.withLock { renderer?.close(); renderer = null; temporary?.delete() }
        }
        super.onCleared()
    }
}
