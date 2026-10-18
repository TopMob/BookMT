package com.TopMob.bookmt.data.parser

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Xml
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

internal fun XmlPullParser.fb2Tag(): String = name?.substringAfterLast(':')?.lowercase().orEmpty()
internal fun XmlPullParser.fb2Attribute(name: String): String? =
    (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfterLast(':') == name }?.let(::getAttributeValue)

/** Only the book's title-info; scanner/document authors and translated-source metadata stay separate. */
internal class Fb2Description {
    private val elements = mutableListOf<String>()
    private var infoDepth = 0
    private var authorDepth = 0
    private var annotationDepth = 0
    private val title = StringBuilder()
    private val annotation = StringBuilder()
    private val authorParts = linkedMapOf<String, StringBuilder>()
    private val authors = mutableListOf<String>()
    val coverIds = linkedSetOf<String>()
    val bookTitle get() = title.toString().trim().replace(Regex("\\s+"), " ")
    val bookAuthor get() = authors.distinct().joinToString(", ").ifBlank { null }
    val description get() = annotation.toString().lines().map { it.trim().replace(Regex("[ \\t]+"), " ") }
        .filter { it.isNotEmpty() }.joinToString("\n").ifBlank { null }

    fun event(parser: XmlPullParser) {
        val tag = parser.fb2Tag()
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                val parent = elements.lastOrNull()
                elements += tag
                if (tag == "title-info" && parent == "description") infoDepth = elements.size
                if (infoDepth > 0) when {
                    tag == "author" && elements.size == infoDepth + 1 -> {
                        authorDepth = elements.size; authorParts.clear()
                    }
                    authorDepth > 0 && elements.size == authorDepth + 1 && tag in setOf("first-name", "middle-name", "last-name", "nickname") -> authorParts[tag] = StringBuilder()
                    tag == "annotation" && elements.size == infoDepth + 1 -> annotationDepth = elements.size
                    tag == "image" && parent == "coverpage" && coverIds.size < 16 ->
                        parser.fb2Attribute("href")?.takeIf { it.startsWith("#") && it.length > 1 }?.let { coverIds += it.drop(1) }
                }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                if (infoDepth == 0) return
                val text = parser.text.orEmpty()
                if (elements.lastOrNull() == "book-title" && elements.size == infoDepth + 1) title.append(text.take((4096 - title.length).coerceAtLeast(0)))
                if (authorDepth > 0 && elements.size == authorDepth + 1) authorParts[elements.lastOrNull()]?.let { it.append(text.take((1024 - it.length).coerceAtLeast(0))) }
                if (annotationDepth > 0) annotation.append(text.take((16_384 - annotation.length).coerceAtLeast(0)))
            }
            XmlPullParser.END_TAG -> {
                if (tag == "author" && elements.size == authorDepth && authorDepth > 0) {
                    val full = listOf("first-name", "middle-name", "last-name").mapNotNull { authorParts[it]?.toString()?.trim()?.takeIf(String::isNotEmpty) }.joinToString(" ")
                    (full.ifBlank { authorParts["nickname"]?.toString()?.trim().orEmpty() }).takeIf { it.isNotEmpty() }?.let { if (authors.size < 100) authors += it }
                    authorDepth = 0; authorParts.clear()
                }
                if (annotationDepth > 0 && tag in setOf("p", "v", "subtitle", "empty-line") && annotation.length < 16_384) annotation.append('\n')
                if (tag == "annotation" && elements.size == annotationDepth) annotationDepth = 0
                if (tag == "title-info" && elements.size == infoDepth) infoDepth = 0
                if (elements.isNotEmpty()) elements.removeAt(elements.lastIndex)
            }
        }
    }
    fun metadata(name: String, cover: ByteArray? = null) = BookMetadata(bookTitle.ifBlank { name.substringBeforeLast('.') }, bookAuthor, cover, description)
}

/** A broken/oversized optional image never discards the book's readable text. */
internal class Fb2BinaryData(private val limit: Int = 20 * 1024 * 1024) {
    private val encoded = StringBuilder()
    private var rejected = false
    fun append(value: String) {
        if (rejected) return
        for (char in value) {
            if (char.isWhitespace()) continue
            if (!(char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "+/=") || encoded.length >= ((limit + 2) / 3) * 4) {
                rejected = true; encoded.clear(); return
            }
            encoded.append(char)
        }
    }
    fun decode(): ByteArray? = if (rejected || encoded.isEmpty()) null else try {
        Base64.decode(encoded.toString(), Base64.DEFAULT).takeIf { it.isNotEmpty() && it.size <= limit }
    } catch (_: IllegalArgumentException) { null }
}

internal object Fb2MetadataReader {
    suspend fun parse(input: InputStream, name: String): BookMetadata {
      return try {
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }
        val header = Fb2Description()
        var selected: Fb2BinaryData? = null
        var events = 0
        var sawRoot = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (++events % 128 == 0) currentCoroutineContext().ensureActive()
            val tag = parser.fb2Tag()
            if (parser.eventType == XmlPullParser.START_TAG && !sawRoot) {
                require(tag == "fictionbook") { "Not a FictionBook document" }; sawRoot = true
            }
            header.event(parser)
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (tag == "binary") selected = if (parser.fb2Attribute("id") in header.coverIds) Fb2BinaryData() else null
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> selected?.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> {
                    if (tag == "description" && header.coverIds.isEmpty()) return header.metadata(name)
                    if (tag == "binary") {
                        val bytes = selected?.decode()
                        selected = null
                        if (bytes != null) {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            if (bounds.outWidth > 0 && bounds.outHeight > 0) return header.metadata(name, bytes)
                        }
                    }
                }
            }
            parser.next()
        }
        require(sawRoot) { "Empty FictionBook document" }
        header.metadata(name)
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) { throw BookParseException("Failed to read FB2 metadata", error) }
    }
}
