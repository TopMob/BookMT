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
}
