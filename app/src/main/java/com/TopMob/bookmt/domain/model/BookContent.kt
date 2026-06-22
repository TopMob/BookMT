package com.TopMob.bookmt.domain.model

/**
 * The fully parsed, in-memory representation of a book's readable content produced by a
 * [com.TopMob.bookmt.domain.parser.BookParser].
 *
 * Text is exposed both as discrete [chapters] (for navigation / TOC) and conceptually as one
 * continuous stream — chapter [Chapter.startOffset] values index into that flattened stream so a
 * single absolute character offset can address any position in the book (used by progress,
 * bookmarks, and the TTS cursor).
 */
data class BookContent(
    val title: String,
    val author: String?,
    val chapters: List<Chapter>,
    val tableOfContents: TableOfContents,
) {
    /** Total length of the flattened text stream in characters. */
    val totalLength: Int get() = chapters.lastOrNull()?.let { it.startOffset + it.text.length } ?: 0

    /** Returns the chapter that contains the given absolute [offset], or null if out of range. */
    fun chapterAt(offset: Int): Chapter? =
        chapters.lastOrNull { offset >= it.startOffset }
            ?.takeIf { offset < it.startOffset + it.text.length }
}

/**
 * A contiguous section of a book.
 *
 * @property startOffset Absolute character offset of this chapter's first character within the
 *           flattened book stream.
 */
data class Chapter(
    val index: Int,
    val title: String,
    val text: String,
    val startOffset: Int,
)
