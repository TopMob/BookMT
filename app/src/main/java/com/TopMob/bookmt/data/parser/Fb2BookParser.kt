package com.TopMob.bookmt.data.parser

import android.util.Xml
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParseException
import com.TopMob.bookmt.domain.parser.BookParser
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * FictionBook 2 (.fb2) parser. FB2 is an XML format; we stream it with [XmlPullParser] (no full DOM
 * in memory) extracting:
 *  - book metadata from `description/title-info` (book-title, author),
 *  - chapters from each `<section>` in `<body>`, using the section's `<title>` for the chapter name
 *    and section nesting depth for TOC level.
 *
 * Cover-image and inline-formatting extraction are intentionally out of scope for this baseline.
 */
class Fb2BookParser(
    private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.FB2

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.FB2 ||
            BookFormat.fromMimeType(mimeType) == BookFormat.FB2

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        withContext(dispatchers.io) {
            val parsed = parseInternal(input, metadataOnly = true)
            BookMetadata(
                title = parsed.title.ifBlank { fileName.substringBeforeLast('.') },
                author = parsed.author,
            )
        }

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        withContext(dispatchers.io) {
            val parsed = parseInternal(input, metadataOnly = false)
            assembleContent(
                title = parsed.title.ifBlank { fileName.substringBeforeLast('.') },
                author = parsed.author,
                rawChapters = parsed.chapters.ifEmpty {
                    listOf(RawChapter("Body", parsed.fallbackBody.toString()))
                },
            )
        }

    private fun parseInternal(input: InputStream, metadataOnly: Boolean): Parsed = try {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

        var title = ""
        var firstName = ""
        var lastName = ""
        val chapters = mutableListOf<RawChapter>()
        val fallbackBody = StringBuilder()

        var inDescription = false
        var inTitleInfo = false
        var inAuthor = false
        var inBody = false
        var sectionDepth = 0
        var inSectionTitle = false

        var pendingChapterTitle: String? = null
        val sectionText = StringBuilder()
        var capturedTag: String? = null // which simple text tag we're currently inside

        fun flushSection() {
            if (sectionText.isNotBlank()) {
                chapters += RawChapter(
                    title = pendingChapterTitle?.takeIf { it.isNotBlank() } ?: "Section ${chapters.size + 1}",
                    text = sectionText.toString().trim() + "\n",
                    level = (sectionDepth - 1).coerceAtLeast(0),
                )
            }
            sectionText.clear()
            pendingChapterTitle = null
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "description" -> inDescription = true
                    "title-info" -> inTitleInfo = inDescription
                    "author" -> inAuthor = inTitleInfo
                    "book-title" -> if (inTitleInfo) capturedTag = "book-title"
                    "first-name" -> if (inAuthor) capturedTag = "first-name"
                    "last-name" -> if (inAuthor) capturedTag = "last-name"
                    "body" -> inBody = true
                    "section" -> if (inBody) {
                        if (sectionDepth == 0) flushSection() // start of a top-level chapter
                        sectionDepth++
                    }
                    "title" -> if (inBody && sectionDepth > 0) inSectionTitle = true
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    when {
                        capturedTag == "book-title" -> title += text
                        capturedTag == "first-name" -> firstName += text.trim()
                        capturedTag == "last-name" -> lastName += text.trim()
                        !metadataOnly && inSectionTitle -> pendingChapterTitle =
                            (pendingChapterTitle ?: "") + text
                        !metadataOnly && inBody && sectionDepth > 0 -> {
                            sectionText.append(text)
                            fallbackBody.append(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                    "description" -> inDescription = false
                    "title-info" -> inTitleInfo = false
                    "author" -> inAuthor = false
                    "book-title", "first-name", "last-name" -> capturedTag = null
                    "title" -> inSectionTitle = false
                    "p" -> if (!metadataOnly && inBody) sectionText.append('\n')
                    "section" -> if (inBody) {
                        sectionDepth--
                        if (sectionDepth == 0) flushSection()
                    }
                    "body" -> {
                        inBody = false
                        if (metadataOnly) return Parsed(
                            title, fullName(firstName, lastName), emptyList(), fallbackBody,
                        )
                    }
                }
            }
            event = parser.next()
        }
        flushSection()
        Parsed(title.trim(), fullName(firstName, lastName), chapters, fallbackBody)
    } catch (t: Throwable) {
        throw BookParseException("Failed to parse FB2 file", t)
    }

    private fun fullName(first: String, last: String): String? =
        listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

    private data class Parsed(
        val title: String,
        val author: String?,
        val chapters: List<RawChapter>,
        val fallbackBody: StringBuilder,
    )
}
