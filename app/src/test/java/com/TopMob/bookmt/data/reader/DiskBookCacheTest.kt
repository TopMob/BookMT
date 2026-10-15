package com.TopMob.bookmt.data.reader

import com.TopMob.bookmt.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskBookCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun storageRoundTripPreservesTextStylesImagesTargetsAndLazyMetadata() {
        val source = temporary.newFile("book.fb2").apply { writeText("source") }
        val cache = DiskBookCache(temporary.newFolder("cache")); val key = cache.key(source, BookFormat.FB2)
        val text = ("Пример текста 😀 со словами. ").repeat(4000)
        cache.writer(key).use { writer ->
            writer.append(Chapter(0, "Глава", text, 0, spans = listOf(ContentSpan(10, text.length - 4, ContentStyle.BOLD)),
                links = listOf(ContentLink(50, 80, "#note"))))
            writer.target("#note", 123)
            writer.resource("cover", byteArrayOf(1, 2, 3).inputStream())
            writer.finish("Книга", "Автор", TableOfContents(listOf(TocEntry("Глава", 0))))
        }
        val content = cache.read(key)!!
        assertTrue(content.chapters is ChapterCollection)
        assertEquals(text.length, content.totalLength)
        assertEquals(text, content.chapters.joinToString("") { it.text })
        assertTrue(content.chapters.all { it.text.length <= DiskBookCache.BLOCK_SIZE && !it.text.last().isHighSurrogate() })
        assertEquals(123, content.targets["#note"])
        assertArrayEquals(byteArrayOf(1, 2, 3), java.io.File(content.resources.getValue("cover")).readBytes())
        assertTrue(content.chapters[1].spans.any { it.style == ContentStyle.BOLD && it.start == 0 })
        // Metadata/navigation do not need to read text blocks from disk.
        java.io.File(content.cacheDirectory, "0.block").delete()
        assertEquals(text.length, content.totalLength)
        assertEquals(0, content.chapterIndexAt(10))
        assertNull(cache.read(key))
    }
    @Test fun cancelledTransactionNeverReplacesCompleteDocument() {
        val source = temporary.newFile("book.txt"); val cache = DiskBookCache(temporary.newFolder("cache")); val key = cache.key(source, BookFormat.TXT)
        cache.writer(key).use { it.append(Chapter(0, "", "old", 0)); it.finish("Old", null, TableOfContents.EMPTY) }
        cache.writer(key).use { it.append(Chapter(0, "", "unfinished", 0)) }
        assertEquals("old", cache.read(key)!!.chapters[0].text)
    }

    @Test fun sameLengthTextCorruptionIsDetectedBeforeRendering() {
        val source = temporary.newFile("book.txt"); val cache = DiskBookCache(temporary.newFolder("cache")); val key = cache.key(source, BookFormat.TXT)
        val content = cache.writer(key).use { it.append(Chapter(0, "", "original text", 0)); it.finish("Book", null, TableOfContents.EMPTY) }
        val block = java.io.File(content.cacheDirectory, "0.block")
        val bytes = block.readBytes(); bytes[4] = 'O'.code.toByte(); block.writeBytes(bytes)
        val reopened = cache.read(key)!!
        assertTrue(runCatching { reopened.chapters[0].text }.isFailure)
    }
}
