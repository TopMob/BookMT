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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var progressSaveJob: Job? = null

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
                val offset = ttsState.currentSegment?.startOffset ?: current.currentOffset
                val pageIndex = if (ttsState.currentSegment != null) {
                    ReaderPaginator.pageIndexForOffset(current.pages, offset)
                } else {
                    current.currentPageIndex
                }
                current.copy(tts = ttsState, currentOffset = offset, currentPageIndex = pageIndex)
            }
        }
    }

    private fun loadContent() = viewModelScope.launch {
        val book = bookRepository.getBook(bookId)
        when (val result = openBook(bookId)) {
            is Resource.Success -> {
                val content = result.data
                val pages = withContext(dispatchers.default) { ReaderPaginator.paginate(content) }
                val startOffset = book?.lastReadPosition ?: 0
                val pageIndex = ReaderPaginator.pageIndexForOffset(pages, startOffset)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        content = content,
                        pages = pages,
                        currentPageIndex = pageIndex,
                        currentOffset = startOffset,
                    )
                }
            }

            is Resource.Error -> _uiState.update {
                it.copy(isLoading = false, errorMessage = result.message ?: "Failed to open book")
            }

            Resource.Loading -> Unit
        }
    }

    /* -------- Navigation within the book -------- */

    fun onPageChanged(pageIndex: Int) {
        val pages = _uiState.value.pages
        val page = pages.getOrNull(pageIndex) ?: return
        _uiState.update { it.copy(currentPageIndex = pageIndex, currentOffset = page.startOffset) }
        scheduleProgressSave(page.startOffset)
    }

    /** Used by scroll mode, which reports the top-most visible character offset. */
    fun onScrollOffsetChanged(offset: Int) {
        _uiState.update {
            it.copy(
                currentOffset = offset,
                currentPageIndex = ReaderPaginator.pageIndexForOffset(it.pages, offset),
            )
        }
        scheduleProgressSave(offset)
    }

    fun onSeek(progress: Float) {
        val total = _uiState.value.totalLength
        val offset = (progress.coerceIn(0f, 1f) * total).toInt()
        jumpToOffset(offset)
    }

    fun jumpToOffset(offset: Int) {
        val pages = _uiState.value.pages
        val pageIndex = ReaderPaginator.pageIndexForOffset(pages, offset)
        _uiState.update { it.copy(currentOffset = offset, currentPageIndex = pageIndex) }
        scheduleProgressSave(offset)
    }

    private fun scheduleProgressSave(offset: Int) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DEBOUNCE_MS)
            updateProgress(bookId, offset, _uiState.value.totalLength)
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
        sessionStartMs = System.currentTimeMillis()
    }

    /** Called when the reader leaves the foreground; persists the elapsed reading time. */
    fun onReadingPaused() {
        val start = sessionStartMs ?: return
        sessionStartMs = null
        val elapsed = System.currentTimeMillis() - start
        if (elapsed <= 0L) return
        // NonCancellable: the write must survive the scope being torn down on navigate-back.
        viewModelScope.launch(NonCancellable) { bookRepository.addReadingTime(bookId, elapsed) }
    }

    /* -------- Settings -------- */

    fun onUpdateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /* -------- Bookmarks / highlights / notes -------- */

    fun onAddBookmarkAtCurrentPage() {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.currentPageIndex) ?: return
        addAnnotation(
            type = BookmarkType.BOOKMARK,
            start = page.startOffset,
            end = page.startOffset,
            snippet = page.text.take(SNIPPET_LENGTH).trim(),
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
        viewModelScope.launch {
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
        viewModelScope.launch { manageBookmarks.delete(bookmarkId) }
    }

    /* -------- TTS (multi-voice read-aloud) -------- */

    fun onPlayTts() {
        val content = _uiState.value.content ?: return
        viewModelScope.launch {
            readAloud.prepare(DefaultVoices.assignment)
            readAloud.start(content, _uiState.value.currentOffset)
        }
    }

    fun onPauseTts() = readAloud.pause()
    fun onResumeTts() = readAloud.resume()
    fun onStopTts() = readAloud.stop()

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        readAloud.stop()
    }

    private companion object {
        const val PROGRESS_SAVE_DEBOUNCE_MS = 800L
        const val SNIPPET_LENGTH = 120
    }
}
