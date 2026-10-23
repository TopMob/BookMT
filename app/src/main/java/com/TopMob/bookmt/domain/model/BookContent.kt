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
    val resources: Map<String, String> = emptyMap(),
    val targets: Map<String, Int> = emptyMap(),
    val cacheKey: String? = null,
    val cacheDirectory: String? = null,
    val revision: Int = 1,
    val positionWarnings: Int = 0,
    val relocatedOffsets: Map<Int, Int> = emptyMap(),
) {
    /** Total length of the flattened text stream in characters. */
    val totalLength: Int get() = descriptor(chapters.lastIndex)?.let { it.startOffset + it.length } ?: 0

    /** Returns the chapter that contains the given absolute [offset], or null if out of range. */
    fun descriptor(index: Int): ChapterDescriptor? = if (index !in chapters.indices) null
        else (chapters as? ChapterCollection)?.descriptor(index) ?: chapters[index].descriptor()

    fun chapterIndexAt(offset: Int): Int {
        if (chapters.isEmpty()) return -1
        var low = 0
        var high = chapters.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (checkNotNull(descriptor(mid)).startOffset <= offset) low = mid else high = mid - 1
        }
        return low
    }

    fun chapterAt(offset: Int): Chapter? {
        val index = chapterIndexAt(offset)
        val descriptor = descriptor(index) ?: return null
        return chapters[index].takeIf { offset in descriptor.startOffset until descriptor.startOffset + descriptor.length }
    }
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
    val sourceId: String = "chapter:$index",
    val sourceOffset: Int = 0,
    val spans: List<ContentSpan> = emptyList(),
    val links: List<ContentLink> = emptyList(),
    val images: List<ContentImage> = emptyList(),
    val auxiliary: Boolean = false,
)

data class ChapterDescriptor(val index: Int, val title: String, val startOffset: Int, val length: Int,
    val sourceId: String, val sourceOffset: Int, val auxiliary: Boolean = false)

fun Chapter.descriptor() = ChapterDescriptor(index, title, startOffset, text.length, sourceId, sourceOffset, auxiliary)

/** Metadata can be searched without loading text; implementations bound the text cache. */
interface ChapterCollection : List<Chapter> { fun descriptor(index: Int): ChapterDescriptor }

enum class ContentStyle { BOLD, ITALIC, UNDERLINE, STRIKE, CODE, HEADING, QUOTE, SUBSCRIPT, SUPERSCRIPT }
data class ContentSpan(val start: Int, val end: Int, val style: ContentStyle, val level: Int = 0)
data class ContentLink(val start: Int, val end: Int, val target: String)
data class ContentImage(val offset: Int, val resourceId: String, val alt: String = "", val width: Int = 0, val height: Int = 0)

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
