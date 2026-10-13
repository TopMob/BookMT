package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Chapter
import com.TopMob.bookmt.domain.model.TableOfContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginatorTest {

    @Test
    fun `pagination preserves text across chapters and word boundaries`() {
        val texts = arrayOf("word \n".repeat(50), "Next chapter 🐈 ".repeat(30))
        val pages = ReaderPaginator.paginate(content(*texts), charBudget = 31)
        assertEquals(texts.joinToString(""), pages.joinToString("") { it.text })
        pages.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
        assertTrue(pages.none { it.text.lastOrNull()?.isHighSurrogate() == true })
    }

    @Test
    fun `layout fitting limits each page and preserves all characters`() {
        val text = "one two three four five ".repeat(100)
        val pages = ReaderPaginator.paginate(content(text), charBudget = 100) { minOf(27, it.length) }
        assertEquals(text, pages.joinToString("") { it.text })
        assertTrue(pages.all { it.text.length <= 27 })
        assertEquals(0, ReaderPaginator.pageIndexForOffset(pages, -1))
        assertEquals(pages.lastIndex, ReaderPaginator.pageIndexForOffset(pages, Int.MAX_VALUE))
    }

    private fun content(vararg texts: String): BookContent {
        var offset = 0
        val chapters = texts.mapIndexed { i, t ->
            Chapter(index = i, title = "C$i", text = t, startOffset = offset).also { offset += t.length }
        }
        return BookContent("Title", null, chapters, TableOfContents.EMPTY)
    }

    @Test
    fun `pages cover the whole stream contiguously`() {
        val text = "word ".repeat(2000) // ~10k chars
        val pages = ReaderPaginator.paginate(content(text), charBudget = 1000)

        assertEquals(0, pages.first().startOffset)
        // Each page should start exactly where the previous ended.
        pages.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
        assertEquals(text.length, pages.last().endOffset)
    }

    @Test
    fun `pages never exceed the char budget`() {
        val pages = ReaderPaginator.paginate(content("a".repeat(5000)), charBudget = 500)
        assertTrue(pages.all { it.text.length <= 500 })
    }

    @Test
    fun `pageIndexForOffset locates the containing page`() {
        val pages = ReaderPaginator.paginate(content("x".repeat(3000)), charBudget = 1000)
        val idx = ReaderPaginator.pageIndexForOffset(pages, offset = 2500)
        assertTrue(pages[idx].startOffset <= 2500 && 2500 < pages[idx].endOffset)
    }

    @Test
    fun `empty content yields a single empty page`() {
        val pages = ReaderPaginator.paginate(content())
        assertEquals(1, pages.size)
        assertEquals("", pages.first().text)
    }
}
