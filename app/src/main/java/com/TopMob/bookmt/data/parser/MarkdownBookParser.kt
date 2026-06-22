package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParser
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Markdown parser. Splits the document on ATX headings (`#`, `##`, …): each heading starts a new
 * chapter, and the heading level maps to TOC nesting. Inline markup is lightly stripped to produce
 * clean reading text while preserving paragraph structure. (Full CommonMark rendering can be layered
 * on later behind the same interface.)
 */
class MarkdownBookParser(
    private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.MARKDOWN

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.MARKDOWN ||
            BookFormat.fromMimeType(mimeType) == BookFormat.MARKDOWN

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        withContext(dispatchers.io) {
            val firstHeading = input.bufferedReader().useLines { lines ->
                lines.firstOrNull { HEADING.matches(it) }
            }
            val title = firstHeading
                ?.let { HEADING.find(it)?.groupValues?.get(2)?.trim() }
                ?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.')
            BookMetadata(title = title)
        }

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        withContext(dispatchers.io) {
            val text = input.readBytes().decodeAsText()
            val rawChapters = splitOnHeadings(text)
            val title = rawChapters.firstOrNull()?.title ?: fileName.substringBeforeLast('.')
            assembleContent(title = title, author = null, rawChapters = rawChapters)
        }

    private fun splitOnHeadings(text: String): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        val body = StringBuilder()
        var title = DEFAULT_TITLE
        var level = 0

        fun flush() {
            if (body.isNotBlank()) {
                chapters += RawChapter(title, body.toString().trim() + "\n", level)
                body.clear()
            }
        }

        for (line in text.lines()) {
            val match = HEADING.find(line)
            if (match != null) {
                flush()
                level = (match.groupValues[1].length - 1).coerceAtLeast(0)
                title = stripInline(match.groupValues[2].trim())
            } else {
                body.append(stripInline(line)).append('\n')
            }
        }
        flush()

        return chapters.ifEmpty { listOf(RawChapter(DEFAULT_TITLE, stripInline(text))) }
    }

    /** Removes the most common inline Markdown markers for clean prose rendering. */
    private fun stripInline(s: String): String = s
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("\\[(.+?)]\\(.+?\\)"), "$1")

    private companion object {
        const val DEFAULT_TITLE = "Document"
        // Group 1 = leading hashes (level), group 2 = heading text.
        val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    }
}
