package com.TopMob.bookmt.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/** Source-relative location plus surrounding text. Offsets remain as a backwards-compatible fallback. */
data class TextAnchor(val sourceId: String, val sourceOffset: Int, val before: String, val after: String) {
    fun encode() = listOf("1", encode(sourceId), sourceOffset.toString(), encode(before), encode(after)).joinToString("\t")
    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        fun decode(value: String?): TextAnchor? = runCatching {
            val parts = value?.split('\t', limit = 5) ?: return null
            if (parts.size != 5 || parts[0] != "1") return null
            TextAnchor(URLDecoder.decode(parts[1], "UTF-8"), parts[2].toInt(), URLDecoder.decode(parts[3], "UTF-8"), URLDecoder.decode(parts[4], "UTF-8"))
        }.getOrNull()
    }
}

object TextAnchors {
    data class Resolution(val offset: Int, val matched: Boolean)
    fun capture(content: BookContent, offset: Int): TextAnchor? {
        if (content.totalLength == 0) return null
        val bounded = offset.coerceIn(0, content.totalLength)
        val d = content.descriptor(content.chapterIndexAt((bounded - 1).coerceAtLeast(0))) ?: return null
        val before = range(content, (bounded - 48).coerceAtLeast(0), bounded)
            .let { if (it.firstOrNull()?.isLowSurrogate() == true) it.drop(1) else it }
        val after = range(content, bounded, (bounded + 96).coerceAtMost(content.totalLength))
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        return TextAnchor(d.sourceId, d.sourceOffset + bounded - d.startOffset, before, after)
    }
    fun range(content: BookContent, start: Int, end: Int): String = buildString {
        var index = content.chapterIndexAt(start)
        while (index in content.chapters.indices) {
            val d = checkNotNull(content.descriptor(index))
            if (d.startOffset >= end) break
            val text = content.chapters[index].text
            append(text.substring((start - d.startOffset).coerceIn(0, text.length), (end - d.startOffset).coerceIn(0, text.length)))
            index++
        }
    }
    private fun normalized(text: String) = text.filterNot { it.isWhitespace() || it == '\uFFFC' || it == '\u00AD' }
    fun resolve(content: BookContent, encoded: String?, fallback: Int): Resolution {
        val bound = fallback.coerceIn(0, content.totalLength)
        val anchor = TextAnchor.decode(encoded) ?: return Resolution(bound, false)
        val after = normalized(anchor.after)
        val before = normalized(anchor.before)
        if (after.isEmpty() && before.isEmpty()) return Resolution(bound, false)
        fun matched(offset: Int): Resolution {
            val leadingAfter = anchor.after.takeWhile { it.isWhitespace() }.length
            val trailingBefore = anchor.before.takeLastWhile { it.isWhitespace() }.length
            if (leadingAfter == 0 && trailingBefore == 0) return Resolution(offset, true)
            var start = offset; var end = offset
            while (start > 0 && offset - start < 1024 && range(content, start - 1, start).all { it.isWhitespace() }) start--
            while (end < content.totalLength && end - offset < 1024 && range(content, end, end + 1).all { it.isWhitespace() }) end++
            return Resolution(if (leadingAfter > 0) start + minOf(trailingBefore, end - start) else end, true)
        }
        val matchingSource = content.chapters.indices.filter { content.descriptor(it)?.sourceId == anchor.sourceId }
        for (index in matchingSource) {
            val d = checkNotNull(content.descriptor(index))
            val local = anchor.sourceOffset - d.sourceOffset
            if (local in 0..d.length) {
                val offset = d.startOffset + local
                val actualAfter = range(content, offset, minOf(content.totalLength, offset + anchor.after.length))
                val actualBefore = range(content, maxOf(0, offset - anchor.before.length), offset)
                if (actualAfter == anchor.after && actualBefore == anchor.before) return Resolution(offset, true)
                if (normalized(actualAfter) == after && normalized(actualBefore) == before) return matched(offset)
            }
        }
        val near = content.chapterIndexAt(bound)
        val candidates = (matchingSource + (near - 2..near + 2).filter { it in content.chapters.indices }).distinct()
        fun search(indices: Iterable<Int>, needle: String, usingAfter: Boolean): Resolution? {
            if (needle.length < 8) return null
            val allMatches = linkedSetOf<Int>()
            val contextualMatches = linkedSetOf<Int>()
            for (index in indices) {
                val d = checkNotNull(content.descriptor(index))
                val base = maxOf(0, d.startOffset - 128)
                val text = range(content, base, minOf(content.totalLength, d.startOffset + d.length + 128))
                val positions = ArrayList<Int>(text.length)
                val clean = buildString { text.forEachIndexed { i, c -> if (!c.isWhitespace() && c != '\uFFFC' && c != '\u00AD') { append(c); positions += i } } }
                var cursor = 0
                while (cursor < clean.length) {
                    val match = clean.indexOf(needle, cursor)
                    if (match < 0) break
                    val local = if (usingAfter) positions[match] else positions[(match + needle.length - 1).coerceAtMost(positions.lastIndex)] + 1
                    val offset = base + local
                    val contextMatches = if (usingAfter) before.isEmpty() || clean.substring(0, match).endsWith(before.takeLast(24))
                        else after.isEmpty() || clean.substring(match + needle.length).startsWith(after.take(24))
                    allMatches += offset
                    if (contextMatches) contextualMatches += offset
                    cursor = match + 1
                }
            }
            if (contextualMatches.size == 1) return matched(contextualMatches.single())
            // A sufficiently long unique quote survives inserted headings even when adjacent
            // context changes. Repeated quotes are left unresolved instead of silently guessing.
            if (allMatches.size == 1 && needle.length >= 24) return matched(allMatches.single())
            return null
        }
        val needles = listOf(after to true, after.take(48) to true, after.take(24) to true,
            before to false, before.takeLast(24) to false).distinct()
        for ((needle, usingAfter) in needles) search(candidates, needle, usingAfter)?.let { return it }
        val candidateSet = candidates.toSet()
        val remainder = content.chapters.indices.filterNot { it in candidateSet }
        for ((needle, usingAfter) in needles) search(remainder, needle, usingAfter)?.let { return it }
        return Resolution(bound, false)
    }
}
