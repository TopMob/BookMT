package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Chapter
import com.TopMob.bookmt.domain.model.TableOfContents
import com.TopMob.bookmt.domain.model.TocEntry

/** A chapter before its absolute offset has been computed. */
internal data class RawChapter(val title: String, val text: String, val level: Int = 0)

/**
 * Assembles [BookContent] from ordered [RawChapter]s, computing each chapter's absolute
 * [Chapter.startOffset] into the flattened stream and building a matching [TableOfContents]. This
 * centralizes the offset bookkeeping every text-based parser needs so individual parsers only worry
 * about splitting their format into chapters.
 */
internal fun assembleContent(
    title: String,
    author: String?,
    rawChapters: List<RawChapter>,
): BookContent {
    if (rawChapters.isEmpty()) {
        return BookContent(title, author, emptyList(), TableOfContents.EMPTY)
    }

    val chapters = ArrayList<Chapter>(rawChapters.size)
    val tocEntries = ArrayList<TocEntry>(rawChapters.size)
    var offset = 0
    rawChapters.forEachIndexed { index, raw ->
        chapters += Chapter(index = index, title = raw.title, text = raw.text, startOffset = offset)
        tocEntries += TocEntry(title = raw.title, offset = offset, level = raw.level)
        offset += raw.text.length
    }
    return BookContent(title, author, chapters, TableOfContents(tocEntries))
}

/** Honor Unicode BOMs; older Russian TXT books commonly use Windows-1251. */
internal fun ByteArray.decodeAsText(): String {
    if (size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte())
        return copyOfRange(2, size).toString(Charsets.UTF_16LE)
    if (size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte())
        return copyOfRange(2, size).toString(Charsets.UTF_16BE)
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(this)).toString().removePrefix("\uFEFF")
    } catch (_: java.nio.charset.CharacterCodingException) {
        toString(java.nio.charset.Charset.forName("windows-1251"))
    }
}
