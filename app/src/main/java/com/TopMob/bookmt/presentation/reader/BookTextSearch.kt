package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class BookSearchResult(val offset: Int, val length: Int, val chapter: String?, val excerpt: String)

object BookTextSearch {
    /** Searches chapter by chapter without allocating another full copy of the book. */
    suspend fun find(content: BookContent, query: String, limit: Int): List<BookSearchResult> {
        val needle = query.trim().take(512)
        if (needle.isEmpty()) return emptyList()
        val results = ArrayList<BookSearchResult>()
        for ((chapterIndex, chapter) in content.chapters.withIndex()) {
            currentCoroutineContext().ensureActive()
            val text = com.TopMob.bookmt.domain.model.TextAnchors.range(content, chapter.startOffset,
                minOf(content.totalLength, chapter.startOffset + chapter.text.length + needle.length - 1))
            var cursor = 0
            while (cursor < chapter.text.length) {
                currentCoroutineContext().ensureActive()
                val index = text.indexOf(needle, cursor, ignoreCase = true)
                if (index < 0 || index >= chapter.text.length) break
                results += BookSearchResult(chapter.startOffset + index, needle.length, chapter.title,
                    text.substring((index - 55).coerceAtLeast(0),
                        (index + needle.length + 90).coerceAtMost(text.length)).replace('\n', ' '))
                if (results.size >= limit) return results
                cursor = index + needle.length
            }
        }
        return results
    }
}
