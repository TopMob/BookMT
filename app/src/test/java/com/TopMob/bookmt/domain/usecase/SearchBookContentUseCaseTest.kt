package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Chapter
import com.TopMob.bookmt.domain.model.TableOfContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBookContentUseCaseTest {

    private val searchUseCase = SearchBookContentUseCase()

    private val sampleContent = BookContent(
        title = "Сказки",
        author = "Народные",
        chapters = listOf(
            Chapter(
                index = 0,
                title = "Глава I",
                text = "В лесу родилась ёлочка, в лесу она росла. Зимой и летом стройная, зелёная была.",
                startOffset = 0,
            ),
            Chapter(
                index = 1,
                title = "Глава II",
                text = "Метель ей пела песенку: «Спи, ёлочка, бай-бай!» Мороз снежком укутывал: «Смотри, не замерзай!»",
                startOffset = 100,
            ),
        ),
        tableOfContents = TableOfContents(emptyList()),
    )

    @Test
    fun searchShortQuery_returnsEmpty() {
        val results = searchUseCase(sampleContent, "а")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchCaseInsensitiveCyrillic_findsMatchesAcrossChapters() {
        val results = searchUseCase(sampleContent, "ЁЛОЧКА")
        assertEquals(2, results.size)

        // First occurrence in Chapter I
        assertEquals(0, results[0].chapterIndex)
        assertEquals("Глава I", results[0].chapterTitle)
        assertEquals(16, results[0].globalOffset)
        assertTrue(results[0].snippet.contains("ёлочка", ignoreCase = true))

        // Second occurrence in Chapter II
        assertEquals(1, results[1].chapterIndex)
        assertEquals("Глава II", results[1].chapterTitle)
        assertEquals(130, results[1].globalOffset)
    }

    @Test
    fun searchNotFound_returnsEmpty() {
        val results = searchUseCase(sampleContent, "Паровоз")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchMaxResults_respectsLimit() {
        // Query must be >= 2 characters
        val results = searchUseCase(sampleContent, "лесу", maxResults = 1)
        assertEquals(1, results.size)
    }
}
