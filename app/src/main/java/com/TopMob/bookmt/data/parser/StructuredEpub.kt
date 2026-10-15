package com.TopMob.bookmt.data.parser

import android.util.Xml
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.parser.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

internal object StructuredEpub {
    private data class Item(val path: String, val media: String, val properties: String)
    private data class Package(val title: String, val author: String?, val spine: List<String>, val items: Map<String, Item>, val cover: String?, val ncx: String?)

    suspend fun metadata(input: InputStream, name: String, directory: File?): BookMetadata = LocalZipBook.copy(input, directory).use { book ->
        val (_, pkg) = packageFile(book)
        val bytes = pkg.cover?.let { book.open(it, 20L * 1024 * 1024)?.use(InputStream::readBytes) }
        BookMetadata(pkg.title.ifBlank { name.substringBeforeLast('.') }, pkg.author, bytes)
    }

    suspend fun parse(input: InputStream, name: String, destination: BookContentDestination, directory: File?): BookContent =
        LocalZipBook.copy(input, directory).use { book ->
            val (_, pkg) = packageFile(book)
            val builder = StructuredBookBuilder(destination)
            val referencedImages = linkedSetOf<String>()
            val job = currentCoroutineContext()
            val spine = pkg.spine.ifEmpty { book.zip.entries().asSequence().filter { it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) }.map { it.name }.toList() }
            val queue = java.util.ArrayDeque<Pair<String, Boolean>>()
            spine.forEach { queue.addLast(it to false) }
            val scheduled = spine.toMutableSet()
            val htmlItems = pkg.items.values.filter { it.media.contains("html") }.map { it.path }.toSet()
            var documentIndex = 0
            while (queue.isNotEmpty()) {
                val (path, auxiliary) = queue.removeFirst()
                val index = documentIndex++
                job.ensureActive()
                val stream = book.open(path) ?: throw BookParseException("EPUB reading document is missing: $path")
                builder.beginSource(path, "Section ${index + 1}", auxiliary)
                builder.anchor(path)
                var hidden = 0
                var inBody = false
                var headingLevel: Int? = null
                var headingOffset = 0
                val headingText = StringBuilder()
                val lists = mutableListOf<Int?>()
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    HtmlBookEvents.parse(reader, { job.ensureActive() }, { tag ->
                        val n = tag.name
                        if (n in setOf("head", "script", "style")) { if (tag.closing) hidden = (hidden - 1).coerceAtLeast(0) else hidden++; return@parse }
                        if (n == "body") { inBody = !tag.closing; return@parse }
                        if (hidden > 0 || !inBody) return@parse
                        if (!tag.closing) {
                            if (n in BLOCKS) builder.newline()
                            tag.attrs["id"]?.let { builder.anchor("$path#$it") }
                            tag.attrs["name"]?.let { builder.anchor("$path#$it") }
                            style(n)?.let { builder.startStyle(n, it) }
                            val css = tag.attrs["style"].orEmpty().lowercase()
                            if ("font-weight" in css && ("bold" in css || "700" in css)) builder.startStyle(n, ContentStyle.BOLD)
                            if ("font-style" in css && "italic" in css) builder.startStyle(n, ContentStyle.ITALIC)
                            if (n.matches(Regex("h[1-6]"))) {
                                headingLevel = n.drop(1).toInt(); headingOffset = builder.position; headingText.clear()
                                builder.startStyle(n, ContentStyle.HEADING, headingLevel!!)
                            }
                            if (n == "a") tag.attrs["href"]?.let { href ->
                                val target = link(path, href)
                                builder.startLink(n, target)
                                val document = target.substringBefore('#')
                                if (document in htmlItems && scheduled.add(document)) queue.addLast(document to true)
                            }
                            if (n == "ol") lists.add(tag.attrs["start"]?.toIntOrNull() ?: 1)
                            if (n == "ul") lists.add(null)
                            if (n == "li") {
                                val number = lists.lastOrNull()
                                builder.append(if (number == null) "• " else "$number. ")
                                if (number != null) lists[lists.lastIndex] = number + 1
                            }
                            if (n == "td" || n == "th") builder.append("  ")
                            if (n == "br" || n == "hr") builder.newline()
                            if (n == "img" || n == "image") {
                                val href = tag.attrs["src"] ?: tag.attrs["href"]
                                if (href != null && !href.contains(':')) {
                                    val resource = LocalZipBook.resolve(path.substringBeforeLast('/', ""), href)
                                    referencedImages += resource
                                    builder.image(resource, tag.attrs["alt"].orEmpty(), tag.attrs["width"]?.toIntOrNull() ?: 0, tag.attrs["height"]?.toIntOrNull() ?: 0)
                                }
                            }
                        } else {
                            builder.endStyle(n); if (n == "a") builder.endLink(n)
                            if (n in setOf("ol", "ul") && lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                            if (n.matches(Regex("h[1-6]"))) {
                                if (builder.toc.isEmpty() || headingOffset == destination.length) builder.setTitle(headingText.toString())
                                builder.heading(headingText.toString(), headingOffset, (headingLevel ?: 1) - 1); headingLevel = null
                            }
                            if (n in BLOCKS) builder.newline()
                        }
                    }, { text ->
                        if (hidden == 0 && inBody) {
                            if (headingLevel != null && headingText.length < 300) headingText.append(text.take(300 - headingText.length))
                            builder.append(text, collapseWhitespace = true)
                        }
                    })
                }
                builder.newline(); builder.flush()
            }
            for (image in referencedImages) {
                job.ensureActive()
                book.open(image, 20L * 1024 * 1024)?.use { destination.resource(image, it) }
            }
            val preferredToc = navigation(book, pkg)
            val toc = preferredToc.mapNotNull { (label, target, level) -> destination.targetOffset(target)?.let { TocEntry(label, it, level) } }
                .sortedWith(compareBy<TocEntry> { it.offset }.thenBy { it.level }).ifEmpty { builder.toc }
            destination.finish(pkg.title.ifBlank { name.substringBeforeLast('.') }, pkg.author, TableOfContents(toc))
        }

    private suspend fun packageFile(book: LocalZipBook): Pair<String, Package> {
        val container = book.open("META-INF/container.xml", 1024 * 1024)?.use { stream ->
            val parser = parser(stream); var found: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                currentCoroutineContext().ensureActive()
                if (parser.eventType == XmlPullParser.START_TAG && parser.local() == "rootfile") found = parser.attr("full-path")
                parser.next()
            }; found
        } ?: throw BookParseException("EPUB has no package file")
        val path = LocalZipBook.resolve("", container)
        val pkg = book.open(path, 4L * 1024 * 1024)?.use { stream ->
            val parser = parser(stream); val items = linkedMapOf<String, Item>(); val spine = mutableListOf<String>()
            var title = ""; var author: String? = null; var capture: String? = null; var coverId: String? = null; var ncxId: String? = null
            val base = path.substringBeforeLast('/', "")
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                currentCoroutineContext().ensureActive()
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.local()) {
                        "title", "creator" -> capture = parser.local()
                        "item" -> {
                            val id = parser.attr("id"); val href = parser.attr("href")
                            if (id != null && href != null && !href.contains(':')) items[id] = Item(LocalZipBook.resolve(base, href), parser.attr("media-type").orEmpty(), parser.attr("properties").orEmpty())
                        }
                        "itemref" -> if (parser.attr("linear") != "no") parser.attr("idref")?.let(spine::add)
                        "meta" -> if (parser.attr("name") == "cover") coverId = parser.attr("content")
                        "spine" -> ncxId = parser.attr("toc")
                    }
                    XmlPullParser.TEXT -> when (capture) { "title" -> title += parser.text; "creator" -> author = author.orEmpty() + parser.text }
                    XmlPullParser.END_TAG -> if (parser.local() == capture) capture = null
                }; parser.next()
            }
            Package(title.trim(), author?.trim(), spine.mapNotNull { items[it]?.path }, items,
                items.values.firstOrNull { "cover-image" in it.properties.split(' ') }?.path ?: items[coverId]?.path, items[ncxId]?.path)
        } ?: throw BookParseException("EPUB package is missing")
        return path to pkg
    }

    private suspend fun navigation(book: LocalZipBook, pkg: Package): List<Triple<String, String, Int>> {
        val result = mutableListOf<Triple<String, String, Int>>()
        val nav = pkg.items.values.firstOrNull { "nav" in it.properties.split(' ') }?.path
        if (nav != null) {
            val job = currentCoroutineContext(); var navDepth = 0; var active = false; var listDepth = 0
            var href: String? = null; val label = StringBuilder()
            book.open(nav, 4L * 1024 * 1024)?.bufferedReader()?.use { reader ->
                HtmlBookEvents.parse(reader, { job.ensureActive() }, { tag ->
                    when (tag.name) {
                        "nav" -> if (!tag.closing) { navDepth++; if (tag.attrs["type"]?.split(' ')?.contains("toc") == true || tag.attrs["role"] == "doc-toc") active = true }
                            else { navDepth--; if (navDepth == 0) active = false }
                        "ol", "ul" -> if (active) listDepth += if (tag.closing) -1 else 1
                        "a" -> if (active) {
                            if (!tag.closing) { href = tag.attrs["href"]; label.clear() }
                            else { href?.let { if (label.isNotBlank()) result += Triple(label.toString().trim(), link(nav, it), (listDepth - 1).coerceAtLeast(0)) }; href = null }
                        }
                    }
                }, { if (active && href != null && label.length < 300) label.append(it.take(300 - label.length)) })
            }
        }
        if (result.isEmpty() && pkg.ncx != null) book.open(pkg.ncx, 4L * 1024 * 1024)?.use { stream ->
            val parser = parser(stream); var depth = -1; var capture = false
            val labels = mutableListOf<StringBuilder>(); val targets = mutableListOf<String?>(); val levels = mutableListOf<Int>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                currentCoroutineContext().ensureActive()
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.local()) {
                        "navPoint" -> { depth++; labels += StringBuilder(); targets += null; levels += depth }
                        "text" -> capture = true
                        "content" -> if (targets.isNotEmpty()) targets[targets.lastIndex] = parser.attr("src")?.let { link(pkg.ncx, it) }
                    }
                    XmlPullParser.TEXT -> if (capture && labels.isNotEmpty()) labels.last().append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.local()) {
                        "text" -> capture = false
                        "navPoint" -> { val t = targets.removeAt(targets.lastIndex); val l = labels.removeAt(labels.lastIndex).toString().trim(); val level = levels.removeAt(levels.lastIndex)
                            if (t != null && l.isNotBlank()) result += Triple(l, t, level); depth-- }
                    }
                }; parser.next()
            }
            // NCX events close children first; reading offsets supply deterministic order later.
        }
        return result
    }
    private fun link(path: String, href: String): String {
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(href)) return href
        val file = if (href.startsWith('#')) path else LocalZipBook.resolve(path.substringBeforeLast('/', ""), href)
        return file + if ('#' in href) "#" + java.net.URLDecoder.decode(href.substringAfter('#').replace("+", "%2B"), "UTF-8") else ""
    }
    private fun parser(input: InputStream) = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }
    private fun XmlPullParser.local() = name?.substringAfterLast(':').orEmpty()
    private fun XmlPullParser.attr(name: String): String? = (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfterLast(':') == name }?.let(::getAttributeValue)
    private fun style(name: String) = when (name) {
        "b", "strong", "th" -> ContentStyle.BOLD; "i", "em" -> ContentStyle.ITALIC; "u" -> ContentStyle.UNDERLINE
        "s", "strike", "del" -> ContentStyle.STRIKE; "code", "pre" -> ContentStyle.CODE; "blockquote" -> ContentStyle.QUOTE
        "sub" -> ContentStyle.SUBSCRIPT; "sup" -> ContentStyle.SUPERSCRIPT; else -> null
    }
    private val BLOCKS = setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "tr")
}
