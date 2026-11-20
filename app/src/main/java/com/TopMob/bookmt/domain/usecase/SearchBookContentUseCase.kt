package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookSearchResult

/**
 * Searches through all chapters of a [BookContent] for occurrences of a text query.
 * Case-insensitive, unicode-aware, and provides contextual snippets for UI highlighting.
 */
class SearchBookContentUseCase {

    operator fun invoke(
        content: BookContent,
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<BookSearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()

        val results = mutableListOf<BookSearchResult>()
        val queryLower = trimmed.lowercase()

        for (chapter in content.chapters) {
            val text = chapter.text
            val textLower = text.lowercase()
            var startIndex = 0

            while (startIndex < textLower.length) {
                val matchIndex = textLower.indexOf(queryLower, startIndex)
                if (matchIndex < 0) break

                val globalOffset = chapter.startOffset + matchIndex
                val snippetStart = (matchIndex - SNIPPET_BEFORE).coerceAtLeast(0)
                val snippetEnd = (matchIndex + trimmed.length + SNIPPET_AFTER).coerceAtMost(text.length)

                val prefix = if (snippetStart > 0) "…" else ""
                val suffix = if (snippetEnd < text.length) "…" else ""
                val rawSnippet = text.substring(snippetStart, snippetEnd)

                val snippet = prefix + rawSnippet.replace('\n', ' ').trim() + suffix
                val matchStartInSnippet = snippet.lowercase().indexOf(queryLower).coerceAtLeast(0)
                val matchEndInSnippet = (matchStartInSnippet + trimmed.length).coerceAtMost(snippet.length)

                results.add(
                    BookSearchResult(
                        globalOffset = globalOffset,
                        chapterIndex = chapter.index,
                        chapterTitle = chapter.title,
                        snippet = snippet,
                        matchStartInSnippet = matchStartInSnippet,
                        matchEndInSnippet = matchEndInSnippet,
                    )
                )

                if (results.size >= maxResults) return results
                startIndex = matchIndex + 1
            }
        }

        return results
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEFAULT_MAX_RESULTS = 100
        private const val SNIPPET_BEFORE = 35
        private const val SNIPPET_AFTER = 55
    }
}
