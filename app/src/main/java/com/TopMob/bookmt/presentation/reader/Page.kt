package com.TopMob.bookmt.presentation.reader

/**
 * A discrete reading page sliced from a [com.TopMob.bookmt.domain.model.BookContent] chapter stream.
 *
 * @property index 0-based page index within the entire book.
 * @property chapterIndex The index of the chapter this page belongs to.
 * @property startOffset Absolute character offset in the flattened book stream where this page starts.
 * @property endOffset Absolute character offset where this page ends (exclusive).
 * @property text The text content displayed on this page.
 */
data class Page(
    val index: Int,
    val chapterIndex: Int = 0,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)
