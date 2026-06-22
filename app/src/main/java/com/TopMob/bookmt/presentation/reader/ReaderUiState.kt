package com.TopMob.bookmt.presentation.reader

import androidx.compose.runtime.Immutable
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.tts.TtsPlaybackState

/** Which transient overlay (if any) is currently shown over the reading surface. */
enum class ReaderOverlay { NONE, SETTINGS, NOTES, TABLE_OF_CONTENTS }

@Immutable
data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val content: BookContent? = null,
    val pages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val currentOffset: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val bookmarks: List<Bookmark> = emptyList(),
    val showControls: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val tts: TtsPlaybackState = TtsPlaybackState(),
    val errorMessage: String? = null,
) {
    val totalLength: Int get() = content?.totalLength ?: 0

    val progress: Float
        get() = if (totalLength <= 0) 0f else (currentOffset.toFloat() / totalLength).coerceIn(0f, 1f)

    val currentChapterTitle: String?
        get() = content?.chapterAt(currentOffset)?.title
}
