package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class BookSearchResult(val offset: Int, val length: Int, val chapter: String?, val excerpt: String)

object BookTextSearch {
    /** Searches chapter by chapter without allocating another full copy of the book. */
    suspend fun find(content: BookContent, query: String, limit: Int): List<BookSearchResult> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val results = ArrayList<BookSearchResult>()
        for (chapter in content.chapters) {
            currentCoroutineContext().ensureActive()
            var cursor = 0
            while (cursor < chapter.text.length) {
                currentCoroutineContext().ensureActive()
                val index = chapter.text.indexOf(needle, cursor, ignoreCase = true)
                if (index < 0) break
                results += BookSearchResult(chapter.startOffset + index, needle.length, chapter.title,
                    chapter.text.substring((index - 55).coerceAtLeast(0),
                        (index + needle.length + 90).coerceAtMost(chapter.text.length)).replace('\n', ' '))
                if (results.size >= limit) return results
                cursor = index + needle.length
            }
        }
        return results
    }
}
