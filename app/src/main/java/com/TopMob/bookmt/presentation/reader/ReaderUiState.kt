package com.TopMob.bookmt.presentation.reader

import androidx.compose.runtime.Immutable
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookSearchResult
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.tts.TtsPlaybackState

/** Which transient overlay (if any) is currently shown over the reading surface. */
enum class ReaderOverlay { NONE, SETTINGS, NOTES, TABLE_OF_CONTENTS, SEARCH }

@Immutable
data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val content: BookContent? = null,
    val pages: List<Page> = emptyList(),
    val currentOffset: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val bookmarks: List<Bookmark> = emptyList(),
    val showControls: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val tts: TtsPlaybackState = TtsPlaybackState(),
    val wordsPerMinute: Int = 220,
    val minutesRemainingInChapter: Int? = null,
    val minutesRemainingInBook: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<BookSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalLength: Int get() = content?.totalLength ?: 0

    val progress: Float
        get() = if (totalLength <= 0) 0f else (currentOffset.toFloat() / totalLength).coerceIn(0f, 1f)

    val currentChapterTitle: String?
        get() = content?.chapterAt(currentOffset)?.title

    val currentPageIndex: Int
        get() = ReaderPaginator.pageIndexForOffset(pages, currentOffset)

    val totalPages: Int
        get() = pages.size.coerceAtLeast(1)
}
