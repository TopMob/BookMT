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
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * EPUB (.epub) parser implemented with only the platform's ZIP + XML facilities — no third-party
 * dependency. An EPUB is a ZIP whose `META-INF/container.xml` points at an OPF package file; the OPF
 * lists the reading order (`<spine>`) of XHTML documents. We:
 *  1. load every zip entry into memory (EPUBs are typically a few MB),
 *  2. read the OPF for title/author and the ordered spine,
 *  3. turn each spine XHTML document into a chapter of plain text (tags stripped).
 *
 * Inline styling, images and embedded fonts are intentionally out of scope for this baseline reader.
 */
class EpubBookParser(
    private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.EPUB

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.EPUB ||
            BookFormat.fromMimeType(mimeType) == BookFormat.EPUB

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        withContext(dispatchers.io) {
            try {
                val entries = readZip(input)
                val opfPath = containerOpfPath(entries)
                val opf = opfPath?.let { entries[it]?.let { bytes -> parseOpf(bytes) } }
                BookMetadata(
                    title = opf?.title?.takeIf { it.isNotBlank() }
                        ?: fileName.substringBeforeLast('.'),
                    author = opf?.author,
                )
            } catch (t: Throwable) {
                // Import must never fail hard on metadata; fall back to the file name.
                BookMetadata(title = fileName.substringBeforeLast('.'))
            }
        }

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        withContext(dispatchers.io) {
            try {
                val entries = readZip(input)
                val opfPath = containerOpfPath(entries)
                    ?: throw BookParseException("EPUB has no OPF package file")
                val opfBytes = entries[opfPath]
                    ?: throw BookParseException("EPUB OPF entry missing: $opfPath")
                val opf = parseOpf(opfBytes)
                val baseDir = opfPath.substringBeforeLast('/', "")

                val rawChapters = opf.spineHrefs.mapNotNull { href ->
                    val entryName = resolve(baseDir, href)
                    val bytes = entries[entryName] ?: entries[href] ?: return@mapNotNull null
                    val (docTitle, text) = extractXhtml(bytes)
                    if (text.isBlank()) null else RawChapter(
                        title = docTitle ?: "Section",
                        text = text + "\n",
                    )
                }.ifEmpty {
                    // No usable spine: concatenate every XHTML-looking entry as one body.
                    val body = entries.entries
                        .filter { it.key.endsWith(".xhtml") || it.key.endsWith(".html") }
                        .joinToString("\n") { extractXhtml(it.value).second }
                    listOf(RawChapter("Body", body.ifBlank { " " }))
                }

                assembleContent(
                    title = opf.title.ifBlank { fileName.substringBeforeLast('.') },
                    author = opf.author,
                    rawChapters = rawChapters,
                )
            } catch (e: BookParseException) {
                throw e
            } catch (t: Throwable) {
                throw BookParseException("Failed to parse EPUB file", t)
            }
        }

    /* -------- ZIP -------- */

    private fun readZip(input: InputStream): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    /* -------- container.xml -> OPF path -------- */

    private fun containerOpfPath(entries: Map<String, ByteArray>): String? {
        val bytes = entries["META-INF/container.xml"] ?: return null
        val parser = newPullParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.localName() == "rootfile") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "full-path") return parser.getAttributeValue(i)
                }
            }
            event = parser.next()
        }
        return null
    }

    /* -------- OPF package -------- */

    private fun parseOpf(bytes: ByteArray): Opf {
        val parser = newPullParser(bytes)
        var title = ""
        var author: String? = null
        val manifest = HashMap<String, String>() // id -> href
        val spine = ArrayList<String>()           // ordered idrefs
        var captured: String? = null              // "title" | "creator"

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    "title" -> captured = "title"
                    "creator" -> captured = "creator"
                    "item" -> {
                        val id = parser.attr("id")
                        val href = parser.attr("href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> parser.attr("idref")?.let { spine += it }
                }

                XmlPullParser.TEXT -> when (captured) {
                    "title" -> if (title.isBlank()) title = parser.text.orEmpty().trim()
                    "creator" -> if (author.isNullOrBlank()) {
                        author = parser.text.orEmpty().trim().ifBlank { null }
                    }
                }

                XmlPullParser.END_TAG -> when (parser.localName()) {
                    "title", "creator" -> captured = null
                }
            }
            event = parser.next()
        }

        val spineHrefs = spine.mapNotNull { manifest[it] }
        return Opf(title.trim(), author, spineHrefs)
    }

    /* -------- XHTML -> plain text -------- */

    private fun extractXhtml(bytes: ByteArray): Pair<String?, String> {
        val raw = bytes.toString(Charsets.UTF_8)
        val title = TITLE.find(raw)?.groupValues?.get(1)?.let { stripTags(it).trim() }
            ?.takeIf { it.isNotBlank() }
            ?: HEADING.find(raw)?.groupValues?.get(1)?.let { stripTags(it).trim() }
                ?.takeIf { it.isNotBlank() }

        var body = BODY.find(raw)?.groupValues?.get(1) ?: raw
        body = body.replace(SCRIPT_STYLE, " ")
        body = body.replace(BLOCK_END, "\n")
        val text = decodeEntities(stripTags(body))
            .lineSequence()
            .map { it.trim() }
            .joinToString("\n")
            .replace(BLANK_LINES, "\n\n")
            .trim()
        return title to text
    }

    private fun stripTags(s: String): String = s.replace(TAG, " ")

    private fun decodeEntities(s: String): String {
        var out = s
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
        out = NUMERIC_ENTITY.replace(out) { m ->
            runCatching { m.groupValues[1].toInt().toChar().toString() }.getOrDefault(" ")
        }
        return out
    }

    /* -------- helpers -------- */

    private fun newPullParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), null)
    }

    private fun XmlPullParser.localName(): String = name?.substringAfterLast(':') ?: ""

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfterLast(':') == name) return getAttributeValue(i)
        }
        return null
    }

    /** Resolves an OPF-relative href (possibly url-encoded, with a fragment) into a zip entry name. */
    private fun resolve(baseDir: String, href: String): String {
        val clean = runCatching { URLDecoder.decode(href.substringBefore('#'), "UTF-8") }
            .getOrDefault(href.substringBefore('#'))
        val combined = if (baseDir.isBlank()) clean else "$baseDir/$clean"
        // Normalize any ".." / "." segments.
        val stack = ArrayList<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += segment
            }
        }
        return stack.joinToString("/")
    }

    private data class Opf(val title: String, val author: String?, val spineHrefs: List<String>)

    private companion object {
        val TITLE = Regex("(?is)<title[^>]*>(.*?)</title>")
        val HEADING = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>")
        val BODY = Regex("(?is)<body[^>]*>(.*?)</body>")
        val SCRIPT_STYLE = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
        val BLOCK_END = Regex("(?i)</(p|div|h[1-6]|li|tr|br)\\s*>|<br[^>]*/?>")
        val TAG = Regex("(?s)<[^>]+>")
        val BLANK_LINES = Regex("\n{3,}")
        val NUMERIC_ENTITY = Regex("&#(\\d+);")
    }
}
