package com.TopMob.bookmt.domain.model

/** A hierarchical table of contents. Flat formats (TXT) yield a single-level list. */
data class TableOfContents(val entries: List<TocEntry>) {
    companion object {
        val EMPTY = TableOfContents(emptyList())
    }
}

/**
 * One TOC node.
 *
 * @property offset Absolute character offset the entry jumps to in the flattened book stream.
 * @property level Nesting depth (0 = top level), used purely for indentation in the UI.
 * @property children Nested entries for formats that express hierarchy (FB2 sections, Markdown
 *           heading levels).
 */
data class TocEntry(
    val title: String,
    val offset: Int,
    val level: Int = 0,
    val children: List<TocEntry> = emptyList(),
)
