package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.graphics.Color
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightAnnotatorTest {

    private val defaultColor = Color(0x66FFEB3B)

    @Test
    fun annotateEmptyBookmarks_returnsPlainAnnotatedString() {
        val result = HighlightAnnotator.annotate(
            text = "Hello world",
            startOffset = 0,
            endOffset = 11,
            bookmarks = emptyList(),
            defaultHighlightColor = defaultColor,
        )
        assertEquals("Hello world", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun annotateIntersectingHighlight_addsSpanStyleWithCorrectOffsets() {
        val bookmarks = listOf(
            Bookmark(
                id = 1L,
                bookId = 1L,
                type = BookmarkType.HIGHLIGHT,
                position = 6,
                endPosition = 11,
                textSnippet = "world",
            ),
        )

        val result = HighlightAnnotator.annotate(
            text = "Hello world",
            startOffset = 0,
            endOffset = 11,
            bookmarks = bookmarks,
            defaultHighlightColor = defaultColor,
        )

        assertEquals("Hello world", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0]
        assertEquals(6, span.start)
        assertEquals(11, span.end)
        assertEquals(defaultColor, span.item.background)
    }

    @Test
    fun annotateNonIntersectingHighlight_isIgnored() {
        val bookmarks = listOf(
            Bookmark(
                id = 1L,
                bookId = 1L,
                type = BookmarkType.HIGHLIGHT,
                position = 50,
                endPosition = 60,
                textSnippet = "far away",
            ),
        )

        val result = HighlightAnnotator.annotate(
            text = "Hello world",
            startOffset = 0,
            endOffset = 11,
            bookmarks = bookmarks,
            defaultHighlightColor = defaultColor,
        )

        assertEquals("Hello world", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun annotatePartialOverlap_clampsToPageBounds() {
        val bookmarks = listOf(
            // Highlight starts before page (offset 5) and ends inside page (offset 15)
            Bookmark(
                id = 1L,
                bookId = 1L,
                type = BookmarkType.HIGHLIGHT,
                position = 5,
                endPosition = 15,
                textSnippet = "test",
            ),
        )

        val result = HighlightAnnotator.annotate(
            text = "0123456789", // page covers offsets 10..20
            startOffset = 10,
            endOffset = 20,
            bookmarks = bookmarks,
            defaultHighlightColor = defaultColor,
        )

        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0]
        // Overlap [10, 15) -> relative to page start (10): local [0, 5)
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }
}
