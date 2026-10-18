package com.TopMob.bookmt.data.parser

import android.util.Xml
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.parser.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

internal object StructuredFb2 {
    suspend fun parse(input: InputStream, name: String, destination: BookContentDestination): BookContent {
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }
        val builder = StructuredBookBuilder(destination)
        val header = Fb2Description()
        var inBody = false; var auxiliary = false
        var bodyIndex = 0; var topIndex = 0; var depth = 0; var paragraphDepth = 0
        var heading = false; var headingStart = 0; val headingText = StringBuilder()
        var sectionHeading = false
        val elements = mutableListOf<String>()
        var binaryId: String? = null; var binary: Fb2BinaryData? = null
        var events = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (++events % 128 == 0) currentCoroutineContext().ensureActive()
            val tag = parser.name?.substringAfterLast(':')?.lowercase().orEmpty()
            header.event(parser)
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val parent = elements.lastOrNull()
                    elements += tag
                    when (tag) {
                        "body" -> {
                            inBody = true; auxiliary = parser.attr("name") in setOf("notes", "comments")
                            builder.beginSource("fb2:body:${bodyIndex++}", if (auxiliary) "Примечания" else header.bookTitle.ifBlank { "Книга" }, auxiliary)
                        }
                        "section" -> if (inBody) {
                            if (depth == 0) builder.beginSource("chapter:${topIndex++}", if (auxiliary) "Примечания" else "Раздел $topIndex", auxiliary)
                            depth++
                        }
                        "title", "subtitle" -> if (inBody) {
                            builder.newline(); heading = true; headingStart = builder.position; headingText.clear()
                            sectionHeading = tag == "title" && parent == "section"
                            builder.startStyle(tag, ContentStyle.HEADING, depth.coerceIn(1, 6))
                        }
                        "p", "v", "text-author" -> if (inBody) { builder.newline(); paragraphDepth++ }
                        "empty-line" -> if (inBody) builder.append("\n")
                        "image" -> if (inBody) parser.attr("href")?.let { builder.image(it.removePrefix("#"), parser.attr("alt").orEmpty()) }
                        "a" -> if (inBody) parser.attr("href")?.let { builder.startLink(tag, it) }
                        "binary" -> { binaryId = parser.attr("id"); binary = if (binaryId != null) Fb2BinaryData() else null }
                    }
                    if (inBody) {
                        parser.attr("id")?.let { builder.anchor("#$it") }
                        style(tag)?.let { builder.startStyle(tag, it) }
                    }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text.orEmpty()
                    when {
                        binaryId != null -> binary?.append(value)
                        inBody && (paragraphDepth > 0 || heading || value.isNotBlank()) -> {
                            if (heading && headingText.length < 300) headingText.append(value.take(300 - headingText.length))
                            builder.append(value, collapseWhitespace = true)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (inBody) { builder.endStyle(tag); if (tag == "a") builder.endLink(tag) }
                    when (tag) {
                        "p", "v", "text-author" -> if (inBody) { paragraphDepth = (paragraphDepth - 1).coerceAtLeast(0); builder.newline() }
                        "title", "subtitle" -> if (inBody) {
                            heading = false
                            if (sectionHeading) {
                                builder.heading(headingText.toString(), headingStart, (depth - 1).coerceAtLeast(0))
                                if (depth == 1) builder.setTitle(headingText.toString())
                            }
                            builder.newline()
                        }
                        "section" -> if (inBody) { depth--; builder.newline() }
                        "body" -> { builder.newline(); builder.flush(); inBody = false }
                        "binary" -> {
                            binaryId?.let { id ->
                                binary?.decode()?.let { bytes -> destination.resource(id, bytes.inputStream()) }
                            }
                            binaryId = null; binary = null
                        }
                    }
                    if (elements.isNotEmpty()) elements.removeAt(elements.lastIndex)
                }
            }
            parser.next()
        }
        builder.flush()
        return destination.finish(header.bookTitle.ifBlank { name.substringBeforeLast('.') }, header.bookAuthor, TableOfContents(builder.toc))
    }
    private fun XmlPullParser.attr(name: String): String? = (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfterLast(':') == name }?.let(::getAttributeValue)
    private fun style(tag: String) = when (tag) {
        "strong" -> ContentStyle.BOLD; "emphasis" -> ContentStyle.ITALIC; "strikethrough" -> ContentStyle.STRIKE
        "code" -> ContentStyle.CODE; "cite", "poem", "epigraph" -> ContentStyle.QUOTE; "text-author" -> ContentStyle.ITALIC; "th" -> ContentStyle.BOLD; "sub" -> ContentStyle.SUBSCRIPT; "sup" -> ContentStyle.SUPERSCRIPT; else -> null
    }
}
