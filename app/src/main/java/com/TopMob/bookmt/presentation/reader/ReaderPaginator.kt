package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.BookContent

/**
 * A single rendered page: a bounded slice of the flattened book stream plus its absolute start
 * offset, used to map pages <-> reading position (progress, bookmarks, TTS cursor).
 */
data class ReaderPage(
    val index: Int,
    val text: String,
    val startOffset: Int,
) {
    val endOffset: Int get() = startOffset + text.length
}

/**
 * Splits book content into fixed-budget [ReaderPage]s at word boundaries.
 *
 * Rationale: rather than relying on (expensive, layout-dependent) exact text measurement, we cap
 * each page at [Constants.READER_PAGE_CHAR_BUDGET] characters and break on whitespace. This makes
 * pagination O(n) and cheap enough to run off the main thread for an entire book, so the reader
 * never blocks the UI — the trade-off is pages aren't filled to the exact pixel, which a future
 * measurement-based paginator can refine behind this same type.
 */
object ReaderPaginator {

    fun paginate(content: BookContent, charBudget: Int = Constants.READER_PAGE_CHAR_BUDGET): List<ReaderPage> {
        val pages = mutableListOf<ReaderPage>()
        var pageIndex = 0

        for (chapter in content.chapters) {
            val text = chapter.text
            var start = 0
            while (start < text.length) {
                val tentativeEnd = (start + charBudget).coerceAtMost(text.length)
                val end = if (tentativeEnd >= text.length) {
                    text.length
                } else {
                    // Back up to the last whitespace so words aren't split across pages.
                    val lastBreak = text.lastIndexOf(' ', tentativeEnd)
                        .coerceAtLeast(text.lastIndexOf('\n', tentativeEnd))
                    if (lastBreak > start) lastBreak + 1 else tentativeEnd
                }
                pages += ReaderPage(
                    index = pageIndex++,
                    text = text.substring(start, end),
                    startOffset = chapter.startOffset + start,
                )
                start = end
            }
        }

        if (pages.isEmpty()) {
            pages += ReaderPage(index = 0, text = "", startOffset = 0)
        }
        return pages
    }

    /** Finds the index of the page containing [offset] (clamped to the valid range). */
    fun pageIndexForOffset(pages: List<ReaderPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val idx = pages.indexOfLast { offset >= it.startOffset }
        return idx.coerceIn(0, pages.lastIndex)
    }
}
