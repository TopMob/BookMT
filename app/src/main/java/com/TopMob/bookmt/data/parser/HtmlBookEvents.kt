package com.TopMob.bookmt.data.parser

import java.io.Reader

/** Bounded tolerant HTML tokenizer. Handles ordinary XHTML and HTML void tags without a DOM. */
internal object HtmlBookEvents {
    data class Tag(val name: String, val attrs: Map<String, String>, val closing: Boolean, val selfClosing: Boolean)
    fun parse(reader: Reader, checkCancelled: () -> Unit, onTag: (Tag) -> Unit, onText: (String) -> Unit) {
        val input = reader.buffered()
        var char = input.read()
        var events = 0
        while (char >= 0) {
            if (++events % 64 == 0) checkCancelled()
            if (char != '<'.code) {
                val value = StringBuilder()
                while (char >= 0 && char != '<'.code && (value.length < 8192 ||
                        (value.lastIndexOf("&") > value.lastIndexOf(";") && value.length < 8256))) {
                    value.append(char.toChar()); char = input.read()
                }
                onText(entities(value.toString()))
                continue
            }
            val raw = StringBuilder()
            char = input.read()
            var quote: Char? = null
            while (char >= 0) {
                val c = char.toChar()
                if (quote == null && c == '>') break
                if (c == '\'' || c == '"') quote = if (quote == c) null else if (quote == null) c else quote
                raw.append(c); require(raw.length <= 64 * 1024) { "HTML tag is too large" }
                char = input.read()
            }
            char = input.read()
            val tag = raw.toString().trim()
            if (tag.startsWith("!--")) {
                // The first '>' may be inside a comment. Consume through '-->' when necessary.
                if (!tag.endsWith("--")) {
                    var tail = ""
                    while (char >= 0 && !tail.endsWith("-->")) { tail = (tail + char.toChar()).takeLast(3); char = input.read() }
                }
                continue
            }
            if (tag.startsWith("!") || tag.startsWith("?")) continue
            val closing = tag.startsWith('/')
            val name = tag.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }.substringAfterLast(':').lowercase()
            if (name.isBlank()) continue
            val attrs = ATTRIBUTE.findAll(tag).associate {
                it.groupValues[1].substringAfterLast(':').lowercase() to entities(it.groupValues[2].ifEmpty { it.groupValues[3] }.ifEmpty { it.groupValues[4] })
            }
            onTag(Tag(name, attrs, closing, tag.endsWith('/') || name in VOID))
        }
    }
    fun entities(text: String): String = ENTITY.replace(text) { match ->
        val value = match.groupValues[1]
        when {
            value.startsWith("#") -> runCatching {
                val number = if (value.startsWith("#x", true)) value.drop(2).toInt(16) else value.drop(1).toInt()
                if (Character.isValidCodePoint(number) && number !in 0xD800..0xDFFF) String(Character.toChars(number)) else "\uFFFD"
            }.getOrDefault("\uFFFD")
            else -> NAMED[value] ?: match.value
        }
    }
    private val ATTRIBUTE = Regex("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]+);")
    private val VOID = setOf("br", "img", "hr", "meta", "link", "input", "source", "wbr", "image")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "copy" to "©", "bull" to "•",
        "thinsp" to " ", "ensp" to " ", "emsp" to " ", "shy" to "\u00AD")
}
