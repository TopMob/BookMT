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

    override suspend fun parseTo(input: InputStream, fileName: String, destination: com.TopMob.bookmt.domain.parser.BookContentDestination): BookContent =
        withContext(dispatchers.io) {
            val builder = StructuredBookBuilder(destination)
            var index = 0
            builder.beginSource("chapter:$index", DEFAULT_TITLE)
            StreamingTextSource.copy(input).use { source -> source.read { fragment ->
                if (fragment.startOfLine && fragment.text.length < 300 && CHAPTER_HEADING.matches(fragment.text.trim())) {
                    if (builder.position > 0) index++
                    builder.beginSource("chapter:$index", fragment.text.trim())
                    builder.heading(fragment.text.trim(), builder.position, 0)
                }
                builder.append(fragment.text)
                if (fragment.endOfLine) builder.append("\n")
            } }
            builder.flush()
            if (builder.toc.isEmpty()) builder.heading(DEFAULT_TITLE, 0, 0)
            destination.finish(fileName.substringBeforeLast('.').ifBlank { fileName }, null,
                com.TopMob.bookmt.domain.model.TableOfContents(builder.toc))
        }

    private companion object {
        const val DEFAULT_TITLE = "Text"
        val CHAPTER_HEADING = Regex(
            pattern = "^(chapter|part|book|глава|часть|книга)\\s+([0-9]+|[ivxlcdm]+|[\\p{L}]+)\\b.*",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
