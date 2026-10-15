package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.parser.BookContentDestination

/** Bounded text buffer shared by streaming XML, HTML and plain-text parsers. */
internal class StructuredBookBuilder(private val destination: BookContentDestination) {
    private val text = StringBuilder()
    private val spans = mutableListOf<ContentSpan>()
    private val links = mutableListOf<ContentLink>()
    private val images = mutableListOf<ContentImage>()
    private data class OpenStyle(val tag: String, val kind: ContentStyle, var start: Int, val level: Int)
    private data class OpenLink(val tag: String, val target: String, var start: Int)
    private val styles = mutableListOf<OpenStyle>()
    private val openLinks = mutableListOf<OpenLink>()
    private var title = "Section"
    private var source = "chapter:0"
    private var sourceBase = 0
    private var auxiliary = false
    private var committedLast: Char? = null
    val toc = mutableListOf<TocEntry>()
    val position get() = destination.length + text.length

    fun beginSource(id: String, name: String, isAuxiliary: Boolean = false) {
        flush()
        styles.clear(); openLinks.clear()
        title = name; source = id; sourceBase = destination.length; auxiliary = isAuxiliary
    }
    fun setTitle(value: String) { if (value.isNotBlank()) title = value.trim().take(300) }
    fun anchor(id: String) { destination.target(id, position) }
    fun heading(value: String, offset: Int, level: Int) {
        if (value.isNotBlank()) toc += TocEntry(value.trim().take(300), offset, level.coerceIn(0, 12))
    }
    fun startStyle(tag: String, style: ContentStyle, level: Int = 0) { styles += OpenStyle(tag, style, position, level) }
    fun endStyle(tag: String) {
        val closing = styles.filter { it.tag == tag }
        closing.forEach { if (it.start < position) spans += ContentSpan(it.start, position, it.kind, it.level) }
        styles.removeAll(closing.toSet())
    }
    fun startLink(tag: String, target: String) { openLinks += OpenLink(tag, target, position) }
    fun endLink(tag: String) {
        val closing = openLinks.filter { it.tag == tag }
        closing.forEach { if (it.start < position) links += ContentLink(it.start, position, it.target) }
        openLinks.removeAll(closing.toSet())
    }
    fun append(value: String, collapseWhitespace: Boolean = false) {
        for (char in value) {
            if (collapseWhitespace && char.isWhitespace()) {
                val previous = text.lastOrNull() ?: committedLast
                if (previous != null && !previous.isWhitespace()) text.append(' ')
            } else text.append(char)
            if ((text.length >= 23_500 && char.isWhitespace()) || (text.length >= 24_000 && !text.last().isHighSurrogate())) flush()
        }
    }
    fun newline() {
        while (text.lastOrNull() == ' ' || text.lastOrNull() == '\t') text.setLength(text.length - 1)
        val previous = text.lastOrNull() ?: committedLast
        if (previous != null && previous != '\n') append("\n")
    }
    fun image(id: String, alt: String = "", width: Int = 0, height: Int = 0) {
        newline()
        images += ContentImage(position, id, alt, width, height)
        append("\uFFFC\n")
    }
    fun flush() {
        if (text.isEmpty()) return
        val base = destination.length
        val stop = position
        val allSpans = spans + styles.map { ContentSpan(it.start, stop, it.kind, it.level) }
        val allLinks = links + openLinks.map { ContentLink(it.start, stop, it.target) }
        destination.append(Chapter(0, title, text.toString(), base, source, base - sourceBase,
            allSpans.mapNotNull { span ->
                val from = maxOf(span.start, base); val to = minOf(span.end, stop)
                if (from < to) span.copy(start = from - base, end = to - base) else null
            }, allLinks.mapNotNull { link ->
                val from = maxOf(link.start, base); val to = minOf(link.end, stop)
                if (from < to) link.copy(start = from - base, end = to - base) else null
            }, images.map { it.copy(offset = it.offset - base) }, auxiliary))
        committedLast = text.lastOrNull()
        text.clear(); spans.clear(); links.clear(); images.clear()
        styles.forEach { it.start = destination.length }; openLinks.forEach { it.start = destination.length }
    }
}

internal class MemoryBookDestination : BookContentDestination {
    private val chapters = mutableListOf<Chapter>()
    private val targets = linkedMapOf<String, Int>()
    override var length = 0; private set
    override fun append(chapter: Chapter) { chapters += chapter.copy(index = chapters.size, startOffset = length); length += chapter.text.length }
    override fun resource(id: String, input: java.io.InputStream, maxBytes: Long) { /* Reader uses the disk destination for images. */ }
    override fun target(id: String, offset: Int) { targets[id] = offset }
    override fun targetOffset(id: String) = targets[id]
    override fun finish(title: String, author: String?, toc: TableOfContents) = BookContent(title, author, chapters, toc, targets = targets, revision = 2)
}
