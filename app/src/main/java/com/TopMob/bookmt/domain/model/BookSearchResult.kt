package com.TopMob.bookmt.domain.model

/**
 * A single match found when searching text inside a book.
 *
 * @property globalOffset Absolute character offset in the book's flattened text stream.
 * @property chapterIndex Index of the chapter containing the match.
 * @property chapterTitle Display title of the chapter.
 * @property snippet Surrounding text context for preview.
 * @property matchStartInSnippet Start index of the matched substring within [snippet].
 * @property matchEndInSnippet End index of the matched substring within [snippet].
 */
data class BookSearchResult(
    val globalOffset: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val matchStartInSnippet: Int,
    val matchEndInSnippet: Int,
)
