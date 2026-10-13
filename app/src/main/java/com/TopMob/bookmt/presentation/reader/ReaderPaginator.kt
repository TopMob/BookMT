package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent

data class ReaderPage(val text: String, val startOffset: Int) {
    val endOffset: Int get() = startOffset + text.length
}

/** Bounded text blocks; optional layout fitting produces pages for the actual viewport. */
object ReaderPaginator {
    fun paginate(
        content: BookContent,
        charBudget: Int = 4000,
        fit: ((String) -> Int)? = null,
    ): List<ReaderPage> {
        require(charBudget > 0)
        val pages = mutableListOf<ReaderPage>()
        for (chapter in content.chapters) {
            var cursor = 0
            while (cursor < chapter.text.length) {
                var end = minOf(cursor + charBudget, chapter.text.length)
                // Avoid splitting surrogate pairs and prefer a word boundary.
                if (end < chapter.text.length && chapter.text[end - 1].isHighSurrogate()) end--
                if (end <= cursor) end = minOf(cursor + 2, chapter.text.length)
                val candidate = chapter.text.substring(cursor, end)
                val fitted = fit?.invoke(candidate)?.coerceIn(1, candidate.length) ?: candidate.length
                end = cursor + fitted
                if (end < chapter.text.length) {
                    val boundary = chapter.text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                    if (boundary >= cursor + fitted / 2) end = boundary + 1
                    if (end > cursor && chapter.text[end - 1].isHighSurrogate()) end--
                }
                if (end <= cursor) end = minOf(cursor + 2, chapter.text.length)
                pages += ReaderPage(chapter.text.substring(cursor, end), chapter.startOffset + cursor)
                cursor = end
            }
        }
        return pages.ifEmpty { listOf(ReaderPage("", 0)) }
    }

    fun pageIndexForOffset(pages: List<ReaderPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        var low = 0
        var high = pages.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (pages[mid].startOffset <= offset) low = mid else high = mid - 1
        }
        return low
    }
}
