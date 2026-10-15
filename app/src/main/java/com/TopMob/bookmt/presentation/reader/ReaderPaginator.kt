package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.*

/** A page owns boundaries, not a second copy of the book. Text is loaded for visible pages only. */
class ReaderPage private constructor(val startOffset: Int, val length: Int, val chapterIndex: Int,
    val localOffset: Int, private val content: BookContent?, private val literal: Chapter?) {
    constructor(text: String, startOffset: Int) : this(startOffset, text.length, -1, 0, null, Chapter(0, "", text, startOffset))
    internal constructor(content: BookContent, chapter: Int, start: Int, end: Int) :
        this(checkNotNull(content.descriptor(chapter)).startOffset + start, end - start, chapter, start, content, null)
    val endOffset get() = startOffset + length
    val text get() = literal?.text ?: checkNotNull(content).chapters[chapterIndex].text.substring(localOffset, localOffset + length)
    fun chapter(): Chapter {
        literal?.let { return it }
        val source = checkNotNull(content).chapters[chapterIndex]
        val end = localOffset + length
        return source.copy(text = source.text.substring(localOffset, end), startOffset = startOffset,
            spans = source.spans.mapNotNull { s -> val from = maxOf(s.start, localOffset); val to = minOf(s.end, end)
                if (from < to) s.copy(start = from - localOffset, end = to - localOffset) else null },
            links = source.links.mapNotNull { l -> val from = maxOf(l.start, localOffset); val to = minOf(l.end, end)
                if (from < to) l.copy(start = from - localOffset, end = to - localOffset) else null },
            images = source.images.filter { it.offset in localOffset until end }.map { it.copy(offset = it.offset - localOffset) })
    }
    fun materialized(): ReaderPage = ReaderPage(startOffset, length, chapterIndex, 0, content, chapter())
    fun resource(id: String): String? = content?.resources?.get(id)
}

object ReaderPaginator {
    fun paginate(content: BookContent, charBudget: Int = 4000, adaptive: Boolean = false,
        checkCancelled: () -> Unit = {}, fit: ((String) -> Int)? = null): List<ReaderPage> =
        content.chapters.indices.flatMap { paginateChapter(content, it, charBudget, adaptive, checkCancelled,
            fit?.let { measure -> { page: ReaderPage -> measure(page.text) } }) }
            .ifEmpty { listOf(ReaderPage("", 0)) }

    fun paginateChapter(content: BookContent, index: Int, charBudget: Int = 8000, adaptive: Boolean = true,
        checkCancelled: () -> Unit = {}, fit: ((ReaderPage) -> Int)? = null): List<ReaderPage> {
        require(charBudget > 0)
        val chapter = content.chapters[index]
        val text = chapter.text
        val pages = mutableListOf<ReaderPage>()
        var cursor = 0
        var window = if (adaptive && fit != null) minOf(2048, charBudget) else charBudget
        while (cursor < text.length) {
            checkCancelled()
            var end: Int; var fitted: Int
            while (true) {
                end = minOf(cursor + window, text.length)
                if (end < text.length && text[end - 1].isHighSurrogate()) end--
                if (end <= cursor) end = minOf(cursor + 2, text.length)
                val candidate = ReaderPage(content, index, cursor, end)
                fitted = fit?.invoke(candidate)?.coerceIn(1, end - cursor) ?: end - cursor
                if (!adaptive || fit == null || fitted < end - cursor || end == text.length || window >= charBudget) break
                window = (window * 2).coerceAtMost(charBudget); checkCancelled()
            }
            if (adaptive && fit != null) window = (fitted + fitted / 3 + 64).coerceIn(minOf(128, charBudget), charBudget)
            end = cursor + fitted
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                if (boundary >= cursor + fitted / 2) end = boundary + 1
                if (end > cursor && text[end - 1].isHighSurrogate()) end--
            }
            if (end <= cursor) end = minOf(cursor + 2, text.length)
            pages += ReaderPage(content, index, cursor, end)
            cursor = end
        }
        return pages
    }
    fun pageIndexForOffset(pages: List<ReaderPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        var low = 0; var high = pages.lastIndex
        while (low < high) { val mid = (low + high + 1) / 2; if (pages[mid].startOffset <= offset) low = mid else high = mid - 1 }
        return low
    }
}
