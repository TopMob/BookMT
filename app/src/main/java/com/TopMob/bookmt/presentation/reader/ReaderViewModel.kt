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
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Owns the entire reading session for a single book: content loading + pagination (off the main
 * thread), live settings/bookmarks, reading-position persistence (debounced), and TTS playback.
 *
 * The screen is a pure function of [uiState]; all mutations go through the intent-style methods.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
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
    private val progressWriteMutex = Mutex()
    private var ttsJob: Job? = null
    private var readingPosition = 0
    private var lastListeningPosition = -1
    private var speechSession = -1L
    private var hasLoaded = false
    private val settingsReady = CompletableDeferred<Unit>()
    private var checkpointOnViewportUpdate = false

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
            _uiState.update { it.copy(settings = settings, settingsLoaded = true) }
            settingsReady.complete(Unit)
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
            if (ttsState.bookId != null && ttsState.bookId != bookId) {
                _uiState.update { it.copy(tts = com.TopMob.bookmt.domain.tts.TtsPlaybackState()) }
                return@collect
            }
            _uiState.update { current ->
                // Keep the on-screen position synced to the spoken text while playing.
                current.copy(tts = ttsState)
            }
            if (ttsJob?.isActive == true && ttsState.status == TtsStatus.PLAYING) {
                ttsState.currentSegment?.let { segment ->
                    val bounded = segment.startOffset.coerceIn(0, (_uiState.value.totalLength - 1).coerceAtLeast(0))
                    _uiState.update { it.copy(currentOffset = bounded) }
                    if (lastListeningPosition != segment.startOffset) {
                        lastListeningPosition = segment.startOffset
                        annotationAction {
                            val content = _uiState.value.content ?: return@annotationAction
                            bookRepository.saveListeningPosition(bookId, segment.startOffset,
                                com.TopMob.bookmt.domain.model.TextAnchors.capture(content, segment.startOffset)?.encode())
                        }
                    }
                }
            }
        }
    }

    private fun loadContent() = viewModelScope.launch {
        ReaderExitWrites.await(bookId)
        val book = bookRepository.getBook(bookId)
        if (book?.format == com.TopMob.bookmt.domain.model.BookFormat.PDF) {
            _uiState.update { it.copy(book = book, isLoading = false) }
            return@launch
        }
        when (val result = openBook(bookId)) {
            is Resource.Success -> {
                // Avoid laying out a cached book with default settings, then throwing
                // that work away as soon as DataStore supplies the user's font/margins.
                settingsReady.await()
                val content = result.data
                val refreshed = bookRepository.getBook(bookId) ?: book
                val explicit = (if (hasLoaded) readingPosition else initialOffset.takeIf { it >= 0 })?.let { offset ->
                    if (book?.contentRevision != content.revision) content.relocatedOffsets[offset] ?: offset else offset
                }
                savedStateHandle[Screen.ARG_OFFSET] = -1
                val restored = kotlinx.coroutines.withContext(dispatchers.io) {
                    com.TopMob.bookmt.domain.model.TextAnchors.resolve(content, refreshed?.positionAnchor, refreshed?.lastReadPosition ?: 0).offset
                }
                val startOffset = (explicit ?: restored)
                    .coerceIn(0, (content.totalLength - 1).coerceAtLeast(0))
                readingPosition = startOffset
                hasLoaded = true
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        content = content,
                        currentOffset = startOffset,
                        navigationVersion = it.navigationVersion + 1,
                        errorMessage = if (content.positionWarnings > 0) "Some older bookmarks could not be accurately mapped to the text. Check them in the bookmarks list." else it.errorMessage,
                    )
                }
                // Write after publishing content so the initial checkpoint includes its anchor.
                persistProgress(startOffset, content.totalLength)
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
        if (_uiState.value.content == null) {
            return
        }
        val bounded = offset.coerceIn(0, (_uiState.value.totalLength - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentOffset = bounded) }
        if (ttsJob?.isActive != true) {
            readingPosition = bounded
            if (checkpointOnViewportUpdate) {
                val length = _uiState.value.totalLength
                ReaderExitWrites.enqueue(viewModelScope, bookId) { persistProgress(bounded, length) }
            } else scheduleProgressSave()
        }
    }

    fun onPageChanged(page: Int, total: Int) {
        _uiState.update { it.copy(currentPage = page, totalPages = kotlin.math.abs(total), paginationReady = total > 0) }
        if (total > 0 && page == total && ttsJob?.isActive != true) scheduleProgressSave()
    }

    fun onPrevChapter() {
        val content = _uiState.value.content ?: return
        val currentOffset = _uiState.value.currentOffset
        val previous = content.tableOfContents.entries.lastOrNull { it.offset < currentOffset }
        jumpToOffset(previous?.offset ?: 0)
    }

    fun onNextChapter() {
        val content = _uiState.value.content ?: return
        val currentOffset = _uiState.value.currentOffset
        content.tableOfContents.entries.firstOrNull { it.offset > currentOffset }?.let { jumpToOffset(it.offset) }
    }

    fun jumpToOffset(offset: Int) {
        if (ttsJob?.isActive == true) onStopTts()
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

    private fun moveToOffset(offset: Int, save: Boolean = true) {
        val bounded = offset.coerceIn(0, (_uiState.value.totalLength - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentOffset = bounded, navigationVersion = it.navigationVersion + 1, canReturn = navigationHistory.isNotEmpty()) }
        if (save) { readingPosition = bounded; scheduleProgressSave() }
    }

    private fun scheduleProgressSave() {
        // Bound the unsaved interval even during continuous scrolling. A debounce
        // postponed every 120 ms could otherwise leave minutes of reading unsaved.
        if (progressSaveJob?.isActive == true) return
        progressSaveJob = viewModelScope.launch {
            do {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                val position = readingPosition
                persistProgress(position, _uiState.value.totalLength)
            } while (position != readingPosition)
        }
    }

    private suspend fun persistProgress(offset: Int, length: Int) {
        // Once a checkpoint starts, navigation/cancellation cannot interrupt it or
        // let an older asynchronous write finish after a newer checkpoint.
        withContext(NonCancellable) {
            progressWriteMutex.withLock {
                try {
                    val state = _uiState.value
                    val progress = if (!state.isLoading && state.paginationReady && state.totalPages > 0 && state.currentPage == state.totalPages && state.content != null && state.currentOffset == offset && ttsJob?.isActive != true && (length <= 0 || offset >= length * 0.75f)) 1f
                        else if (length > 0) (offset.toFloat() / length).coerceIn(0f, 1f) else 0f
                    val anchor = kotlinx.coroutines.withContext(dispatchers.io) { state.content?.let { com.TopMob.bookmt.domain.model.TextAnchors.capture(it, offset)?.encode() } }
                    bookRepository.saveReadingPosition(bookId, progress, offset, anchor, state.content?.revision ?: 2)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save reading progress") }
                }
            }
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
        checkpointOnViewportUpdate = false
        if (sessionStartMs == null) sessionStartMs = SystemClock.elapsedRealtime()
    }

    /** Called when the reader leaves the foreground; persists the elapsed reading time. */
    fun onReadingPaused() {
        // Disposal can deliver the final viewport after ON_PAUSE or even onCleared.
        // Such a callback must flush directly, not launch a timer in a cancelled scope.
        checkpointOnViewportUpdate = true
        _uiState.update { it.copy(autoPaging = false) }
        progressSaveJob?.cancel()
        val state = _uiState.value
        val position = readingPosition
        if (state.content != null) {
            ReaderExitWrites.enqueue(viewModelScope, bookId) { persistProgress(position, state.totalLength) }
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
        val boundedQuery = query.take(512)
        _uiState.update { it.copy(searchQuery = boundedQuery, searchBusy = boundedQuery.isNotBlank(), searchResults = emptyList(), searchLimited = false) }
        val content = _uiState.value.content ?: return
        searchJob = viewModelScope.launch {
            delay(250)
            try {
                val results = kotlinx.coroutines.withContext(dispatchers.default) { BookTextSearch.find(content, boundedQuery, 1001) }
                _uiState.update { it.copy(searchResults = results.take(1000), searchBusy = false, searchLimited = results.size > 1000) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(searchBusy = false, errorMessage = error.message ?: "Failed to search book content") }
            }
        }
    }

    fun toggleAutoPaging() = _uiState.update { it.copy(autoPaging = !it.autoPaging, showControls = false) }
    fun stopAutoPaging() = _uiState.update { it.copy(autoPaging = false) }

    fun onUpdateBookmark(bookmark: Bookmark) = annotationAction { manageBookmarks.update(bookmark) }

    private fun annotationAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { kotlinx.coroutines.withContext(dispatchers.io) { action() } }
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
        if (state.content == null) return
        addAnnotation(
            type = BookmarkType.BOOKMARK,
            start = offset,
            end = offset,
            snippet = null,
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
        snippet: String?,
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
                    textSnippet = snippet ?: _uiState.value.content?.let {
                        com.TopMob.bookmt.domain.model.TextAnchors.range(it, start, minOf(it.totalLength, start + SNIPPET_LENGTH)).trim()
                    }.orEmpty(),
                    note = note,
                    colorArgb = colorArgb,
                    chapterTitle = _uiState.value.content?.let { it.descriptor(it.chapterIndexAt(start))?.title },
                    startAnchor = _uiState.value.content?.let { com.TopMob.bookmt.domain.model.TextAnchors.capture(it, start)?.encode() },
                    endAnchor = _uiState.value.content?.let { com.TopMob.bookmt.domain.model.TextAnchors.capture(it, end)?.encode() },
                    contentRevision = _uiState.value.content?.revision ?: 2,
                ),
            )
        }
    }

    fun onDeleteBookmark(bookmarkId: Long) {
        annotationAction { manageBookmarks.delete(bookmarkId) }
    }

    /* -------- TTS (multi-voice read-aloud) -------- */

    fun onPlayTts(resumeSaved: Boolean = false) {
        val content = _uiState.value.content ?: return
        if (!_uiState.value.settings.ttsLicenseAccepted) return
        if (ttsJob?.isActive == true) return
        stopAutoPaging()
        val settings = _uiState.value.settings
        val visibleOffset = _uiState.value.currentOffset
        val saved = _uiState.value.book
        ttsJob = viewModelScope.launch {
            try {
                val offset = if (resumeSaved && saved != null) kotlinx.coroutines.withContext(dispatchers.io) {
                    com.TopMob.bookmt.domain.model.TextAnchors.resolve(content, saved.listeningAnchor, saved.listeningPosition).offset
                } else visibleOffset
                readAloud.prepare(DefaultVoices.forSettings(settings), bookId)
                speechSession = readAloud.state.value.sessionId
                readAloud.start(content, offset, settings.ttsSentencePauseMs, settings.ttsParagraphPauseMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Read aloud is unavailable") }
            } finally {
                if (speechSession == readAloud.state.value.sessionId && readAloud.state.value.status in setOf(TtsStatus.PLAYING, TtsStatus.PAUSED, TtsStatus.PREPARING)) readAloud.release()
            }
        }
    }

    fun onPauseTts() { if (speechSession == readAloud.state.value.sessionId) readAloud.pause() }
    fun onResumeTts() { if (speechSession == readAloud.state.value.sessionId) readAloud.resume() }
    fun onStopTts() {
        ttsJob?.cancel()
        if (speechSession == readAloud.state.value.sessionId) readAloud.stop()
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    fun onContentError(error: Throwable) = _uiState.update { it.copy(errorMessage = error.message ?: "Failed to open book content") }

    fun onRebuildReadingCache() {
        val speech = ttsJob
        onStopTts()
        _uiState.update { it.copy(content = null, isLoading = true, paginationReady = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                speech?.join()
                bookRepository.invalidateContent(bookId)
                loadContent().join()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
        }
    }

    override fun onCleared() {
        onReadingPaused()
        super.onCleared()
        if (speechSession == readAloud.state.value.sessionId) readAloud.release()
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 250L
        const val SNIPPET_LENGTH = 120
    }
}
