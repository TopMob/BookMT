package com.TopMob.bookmt.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.data.tts.DefaultVoices
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.repository.ReaderSettingsRepository
import com.TopMob.bookmt.domain.usecase.ManageBookmarksUseCase
import com.TopMob.bookmt.domain.usecase.OpenBookUseCase
import com.TopMob.bookmt.domain.usecase.ReadAloudUseCase
import com.TopMob.bookmt.domain.usecase.UpdateReadingProgressUseCase
import com.TopMob.bookmt.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import android.os.SystemClock
import com.TopMob.bookmt.domain.tts.TtsStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the entire reading session for a single book: content loading + pagination (off the main
 * thread), live settings/bookmarks, reading-position persistence (debounced), and TTS playback.
 *
 * The screen is a pure function of [uiState]; all mutations go through the intent-style methods.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val openBook: OpenBookUseCase,
    private val updateProgress: UpdateReadingProgressUseCase,
    private val manageBookmarks: ManageBookmarksUseCase,
    private val settingsRepository: ReaderSettingsRepository,
    private val bookRepository: BookRepository,
    private val readAloud: ReadAloudUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Screen.ARG_BOOK_ID]) {
        "ReaderViewModel requires a '${Screen.ARG_BOOK_ID}' argument"
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    private val initialOffset: Int = savedStateHandle[Screen.ARG_OFFSET] ?: -1
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val navigationHistory = ArrayDeque<Int>()
    private var progressSaveJob: Job? = null
    private var ttsJob: Job? = null

    /** Wall-clock start of the current foreground reading session, or null when paused. */
    private var sessionStartMs: Long? = null

    init {
        observeSettings()
        observeBook()
        observeBookmarks()
        observeTts()
        loadContent()
    }

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.settings.collect { settings ->
            _uiState.update { it.copy(settings = settings) }
        }
    }

    private fun observeBook() = viewModelScope.launch {
        bookRepository.observeBook(bookId).collect { book ->
            _uiState.update { it.copy(book = book) }
        }
    }

    private fun observeBookmarks() = viewModelScope.launch {
        manageBookmarks.observe(bookId).collect { bookmarks ->
            _uiState.update { it.copy(bookmarks = bookmarks) }
        }
    }

    private fun observeTts() = viewModelScope.launch {
        readAloud.state.collect { ttsState ->
            _uiState.update { current ->
                // Keep the on-screen position synced to the spoken text while playing.
                current.copy(tts = ttsState)
            }
            if (ttsState.status == TtsStatus.PLAYING) {
                ttsState.currentSegment?.let { moveToOffset(it.startOffset) }
            }
        }
    }

    private fun loadContent() = viewModelScope.launch {
        val book = bookRepository.getBook(bookId)
        if (book?.format == com.TopMob.bookmt.domain.model.BookFormat.PDF) {
            _uiState.update { it.copy(book = book, isLoading = false) }
            return@launch
        }
        when (val result = openBook(bookId)) {
            is Resource.Success -> {
                val content = result.data
                val startOffset = (initialOffset.takeIf { it >= 0 } ?: book?.lastReadPosition ?: 0)
                    .coerceIn(0, (content.totalLength - 1).coerceAtLeast(0))
                // Opening the first page must still make this the most recently read book.
                persistProgress(startOffset, content.totalLength)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        content = content,
                        currentOffset = startOffset,
                        navigationVersion = it.navigationVersion + 1,
                    )
                }
            }

            is Resource.Error -> _uiState.update {
                it.copy(isLoading = false, showControls = true, errorMessage = result.message ?: "Failed to open book")
            }

            Resource.Loading -> Unit
        }
    }

    /* -------- Navigation within the book -------- */

    /** Used by scroll mode, which reports the top-most visible character offset. */
    fun onScrollOffsetChanged(offset: Int) {
        if (_uiState.value.content == null) return
        val bounded = offset.coerceIn(0, (_uiState.value.totalLength - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentOffset = bounded) }
        scheduleProgressSave(bounded)
    }

    fun onPageChanged(page: Int, total: Int) {
        _uiState.update { it.copy(currentPage = page, totalPages = total, paginationReady = true) }
        if (page == total) scheduleProgressSave(_uiState.value.currentOffset)
    }

    fun onPrevChapter() {
        val content = _uiState.value.content ?: return
        val currentOffset = _uiState.value.currentOffset
        val currentChapter = content.chapterAt(currentOffset) ?: return
        val index = content.chapters.indexOf(currentChapter)
        val prevChapter = content.chapters.getOrNull(index - 1)
        if (prevChapter != null) {
            jumpToOffset(prevChapter.startOffset)
        } else {
            jumpToOffset(0)
        }
    }

    fun onNextChapter() {
        val content = _uiState.value.content ?: return
        val currentOffset = _uiState.value.currentOffset
        val currentChapter = content.chapterAt(currentOffset) ?: return
        val index = content.chapters.indexOf(currentChapter)
        val nextChapter = content.chapters.getOrNull(index + 1)
        if (nextChapter != null) {
            jumpToOffset(nextChapter.startOffset)
        }
    }

    fun jumpToOffset(offset: Int) {
        val old = _uiState.value.currentOffset
        if (old != offset) {
            if (navigationHistory.lastOrNull() != old) navigationHistory.addLast(old)
            if (navigationHistory.size > 50) navigationHistory.removeFirst()
        }
        moveToOffset(offset)
    }

    fun returnToPreviousPosition() {
        navigationHistory.removeLastOrNull()?.let(::moveToOffset)
    }

    private fun moveToOffset(offset: Int) {
        val bounded = offset.coerceIn(0, (_uiState.value.totalLength - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentOffset = bounded, navigationVersion = it.navigationVersion + 1, canReturn = navigationHistory.isNotEmpty()) }
        scheduleProgressSave(bounded)
    }

    private fun scheduleProgressSave(offset: Int) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DEBOUNCE_MS)
            persistProgress(offset, _uiState.value.totalLength)
        }
    }

    private suspend fun persistProgress(offset: Int, length: Int) {
        try {
            val state = _uiState.value
            if (!state.isLoading && state.paginationReady && state.totalPages > 0 && state.currentPage == state.totalPages && state.content != null) {
                bookRepository.updateProgress(bookId, 1f, offset)
            } else updateProgress(bookId, offset, length)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save reading progress") }
        }
    }

    /* -------- Controls / overlays -------- */

    fun onToggleControls() = _uiState.update { it.copy(showControls = !it.showControls) }

    fun onShowOverlay(overlay: ReaderOverlay) =
        _uiState.update { it.copy(overlay = overlay, showControls = false) }

    fun onDismissOverlay() = _uiState.update { it.copy(overlay = ReaderOverlay.NONE) }

    /* -------- Reading-time statistics -------- */

    /** Called when the reader returns to the foreground; starts a new timing session. */
    fun onReadingResumed() {
        if (sessionStartMs == null) sessionStartMs = SystemClock.elapsedRealtime()
    }

    /** Called when the reader leaves the foreground; persists the elapsed reading time. */
    fun onReadingPaused() {
        _uiState.update { it.copy(autoPaging = false) }
        progressSaveJob?.cancel()
        val state = _uiState.value
        if (state.content != null) {
            viewModelScope.launch(NonCancellable) { persistProgress(state.currentOffset, state.totalLength) }
        }
        val start = sessionStartMs ?: return
        sessionStartMs = null
        val elapsed = SystemClock.elapsedRealtime() - start
        if (elapsed <= 0L) return
        // NonCancellable: the write must survive the scope being torn down on navigate-back.
        viewModelScope.launch(NonCancellable) {
            try {
                bookRepository.addReadingTime(bookId, elapsed)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save reading time") }
            }
        }
    }

    fun onSearchChange(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchBusy = query.isNotBlank(), searchResults = emptyList(), searchLimited = false) }
        val content = _uiState.value.content ?: return
        searchJob = viewModelScope.launch {
            delay(250)
            val results = kotlinx.coroutines.withContext(dispatchers.default) { BookTextSearch.find(content, query, 1001) }
            _uiState.update { it.copy(searchResults = results.take(1000), searchBusy = false, searchLimited = results.size > 1000) }
        }
    }

    fun toggleAutoPaging() = _uiState.update { it.copy(autoPaging = !it.autoPaging, showControls = false) }
    fun stopAutoPaging() = _uiState.update { it.copy(autoPaging = false) }

    fun onUpdateBookmark(bookmark: Bookmark) = annotationAction { manageBookmarks.update(bookmark) }

    private fun annotationAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message ?: "Unable to save annotation") } }
    }

    /* -------- Settings -------- */

    fun onUpdateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        annotationAction { settingsRepository.update(transform) }
    }

    /* -------- Bookmarks / highlights / notes -------- */

    fun onAddBookmarkAtCurrentPage() {
        val state = _uiState.value
        val offset = state.currentOffset
        val chapter = state.content?.chapterAt(offset) ?: return
        val localOffset = (offset - chapter.startOffset).coerceIn(0, chapter.text.length)
        val snippet = chapter.text.substring(localOffset).take(SNIPPET_LENGTH).trim()
        addAnnotation(
            type = BookmarkType.BOOKMARK,
            start = offset,
            end = offset,
            snippet = snippet,
        )
    }

    fun onHighlight(start: Int, end: Int, snippet: String, colorArgb: Int?) =
        addAnnotation(BookmarkType.HIGHLIGHT, start, end, snippet, colorArgb = colorArgb)

    fun onAddNote(start: Int, end: Int, snippet: String, note: String) =
        addAnnotation(BookmarkType.NOTE, start, end, snippet, note = note)

    private fun addAnnotation(
        type: BookmarkType,
        start: Int,
        end: Int,
        snippet: String,
        note: String? = null,
        colorArgb: Int? = null,
    ) {
        annotationAction {
            manageBookmarks.add(
                Bookmark(
                    bookId = bookId,
                    type = type,
                    position = start,
                    endPosition = end,
                    textSnippet = snippet,
                    note = note,
                    colorArgb = colorArgb,
                    chapterTitle = _uiState.value.content?.chapterAt(start)?.title,
                ),
            )
        }
    }

    fun onDeleteBookmark(bookmarkId: Long) {
        annotationAction { manageBookmarks.delete(bookmarkId) }
    }

    /* -------- TTS (multi-voice read-aloud) -------- */

    fun onPlayTts() {
        val content = _uiState.value.content ?: return
        if (ttsJob?.isActive == true) return
        ttsJob = viewModelScope.launch {
            try {
                readAloud.prepare(DefaultVoices.assignment)
                readAloud.start(content, _uiState.value.currentOffset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Read aloud is unavailable") }
            }
        }
    }

    fun onPauseTts() = readAloud.pause()
    fun onResumeTts() = readAloud.resume()
    fun onStopTts() {
        ttsJob?.cancel()
        readAloud.stop()
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        onReadingPaused()
        super.onCleared()
        readAloud.stop()
    }

    private companion object {
        const val PROGRESS_SAVE_DEBOUNCE_MS = 800L
        const val SNIPPET_LENGTH = 120
    }
}
