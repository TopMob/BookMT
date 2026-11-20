package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Chapter
import com.TopMob.bookmt.domain.model.TableOfContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginatorTest {

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

    @Test
    fun `multi-chapter book maintains contiguous global offsets`() {
        val c1 = "First chapter text. ".repeat(100)
        val c2 = "Second chapter text. ".repeat(100)
        val pages = ReaderPaginator.paginate(content(c1, c2), charBudget = 500)

        assertEquals(0, pages.first().startOffset)
        pages.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
        assertEquals(c1.length + c2.length, pages.last().endOffset)
    }

    @Test
    fun `handles mixed empty and non-empty chapters gracefully`() {
        val pages = ReaderPaginator.paginate(content("", "Chapter two content", ""), charBudget = 500)
        assertTrue(pages.isNotEmpty())
        assertEquals("Chapter two content", pages.first().text)
    }

    @Test
    fun `pageIndexForOffset boundary conditions`() {
        val pages = ReaderPaginator.paginate(content("a".repeat(3000)), charBudget = 1000)
        // Offset below 0 clamps to 0
        assertEquals(0, ReaderPaginator.pageIndexForOffset(pages, offset = -10))
        // Offset 0 maps to first page
        assertEquals(0, ReaderPaginator.pageIndexForOffset(pages, offset = 0))
        // Exact boundary of page 0/1 (offset 1000) maps to page 1
        assertEquals(1, ReaderPaginator.pageIndexForOffset(pages, offset = 1000))
        // Offset beyond max clamps to last page
        assertEquals(pages.lastIndex, ReaderPaginator.pageIndexForOffset(pages, offset = 50000))
    }
}
