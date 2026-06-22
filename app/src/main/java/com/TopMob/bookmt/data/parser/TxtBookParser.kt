package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParser
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Plain-text parser. Since `.txt` carries no structure, chapters are inferred heuristically from
 * lines that look like chapter headings (e.g. "Chapter 1", "CHAPTER ONE", "PART II"). If none are
 * found the whole file becomes a single chapter.
 */
class TxtBookParser(
    private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.TXT

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.TXT ||
            BookFormat.fromMimeType(mimeType) == BookFormat.TXT

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        BookMetadata(title = fileName.substringBeforeLast('.').ifBlank { fileName })

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        withContext(dispatchers.io) {
            val text = input.readBytes().decodeAsText()
            val rawChapters = splitIntoChapters(text)
            assembleContent(
                title = fileName.substringBeforeLast('.').ifBlank { fileName },
                author = null,
                rawChapters = rawChapters,
            )
        }

    private fun splitIntoChapters(text: String): List<RawChapter> {
        val lines = text.lines()
        val chapters = mutableListOf<RawChapter>()
        val current = StringBuilder()
        var currentTitle = DEFAULT_TITLE

        fun flush() {
            if (current.isNotEmpty()) {
                chapters += RawChapter(currentTitle, current.toString())
                current.clear()
            }
        }

        for (line in lines) {
            if (CHAPTER_HEADING.matches(line.trim())) {
                flush()
                currentTitle = line.trim()
            }
            current.append(line).append('\n')
        }
        flush()

        return chapters.ifEmpty { listOf(RawChapter(DEFAULT_TITLE, text)) }
    }

    private companion object {
        const val DEFAULT_TITLE = "Text"
        val CHAPTER_HEADING = Regex(
            pattern = "^(chapter|part|book)\\s+([0-9]+|[ivxlcdm]+|[a-z]+)\\b.*",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
