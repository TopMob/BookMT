package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAnnotationsUseCaseTest {

    private val exportUseCase = ExportAnnotationsUseCase()

    private val sampleBook = Book(
        id = 1L,
        uri = "file:///storage/emulated/0/Books/war_and_peace.epub",
        title = "Война и мир",
        author = "Лев Толстой",
        format = BookFormat.EPUB,
    )

    @Test
    fun exportEmptyAnnotations_producesHeaderWithEmptyNote() {
        val result = exportUseCase(sampleBook, emptyList())
        assertTrue(result.contains("# Война и мир"))
        assertTrue(result.contains("**Автор:** Лев Толстой"))
        assertTrue(result.contains("*Нет сохраненных заметок или цитат.*"))
    }

    @Test
    fun exportWithHighlightsAndNotes_formatsMarkdownProperly() {
        val annotations = listOf(
            Bookmark(
                id = 1L,
                bookId = 1L,
                type = BookmarkType.HIGHLIGHT,
                position = 100,
                textSnippet = "Все счастливые семьи похожи друг на друга",
                chapterTitle = "Часть первая",
            ),
            Bookmark(
                id = 2L,
                bookId = 1L,
                type = BookmarkType.NOTE,
                position = 250,
                textSnippet = "Мысль народная",
                note = "Ключевая тема эпопеи",
                chapterTitle = "Часть первая",
            ),
        )

        val result = exportUseCase(sampleBook, annotations)
        assertTrue(result.contains("### Часть первая"))
        assertTrue(result.contains("> Все счастливые семьи похожи друг на друга"))
        assertTrue(result.contains("> Мысль народная"))
        assertTrue(result.contains("*Заметка:* Ключевая тема эпопеи"))
    }
}
