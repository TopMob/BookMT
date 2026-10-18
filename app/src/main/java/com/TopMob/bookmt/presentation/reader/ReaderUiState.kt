package com.TopMob.bookmt.presentation.reader

import androidx.compose.runtime.Immutable
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.tts.TtsPlaybackState

/** Which transient overlay (if any) is currently shown over the reading surface. */
enum class ReaderOverlay { NONE, SETTINGS, NOTES, TABLE_OF_CONTENTS, SEARCH, GO_TO }

@Immutable
data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val content: BookContent? = null,
    val currentOffset: Int = 0,
    val navigationVersion: Long = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val paginationReady: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsLoaded: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val showControls: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val tts: TtsPlaybackState = TtsPlaybackState(),
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<BookSearchResult> = emptyList(),
    val searchBusy: Boolean = false,
    val searchLimited: Boolean = false,
    val canReturn: Boolean = false,
    val autoPaging: Boolean = false,
) {
    val totalLength: Int get() = content?.totalLength ?: 0

    val progress: Float
        get() = if (totalLength <= 0) 0f else (currentOffset.toFloat() / totalLength).coerceIn(0f, 1f)

    val currentChapterTitle: String?
        get() = content?.let { it.descriptor(it.chapterIndexAt(currentOffset))?.title }
}
