package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Chapter
import com.TopMob.bookmt.domain.model.TableOfContents
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderPageCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val style = TextStyle(fontSize = 18.sp)
    private fun content() = BookContent("Book", null,
        listOf(Chapter(0, "First", "a".repeat(800), 0), Chapter(1, "Second", "b".repeat(800), 800)),
        TableOfContents.EMPTY, cacheKey = "source", cacheDirectory = temporary.root.absolutePath)

    @Test fun `a completed block survives reopening before the whole book is paginated`() {
        val book = content()
        val pages = ReaderPaginator.paginateChapter(book, 1, charBudget = 200)
        ReaderPageCache.putChapter(book, style, 300, 500, 1f, 1f, true, 1, pages)
        val reopened = content()
        val restored = ReaderPageCache.getChapter(reopened, style, 300, 500, 1f, 1f, true, 1)
        assertEquals(pages.map { it.startOffset to it.endOffset }, restored?.map { it.startOffset to it.endOffset })
        assertNull(ReaderPageCache.getChapter(reopened, style, 300, 500, 1f, 1f, true, 0))
        assertNull(ReaderPageCache.getChapter(reopened, style, 301, 500, 1f, 1f, true, 1))
        assertNull(ReaderPageCache.getChapter(reopened, style.copy(fontSize = 22.sp), 300, 500, 1f, 1f, true, 1))
        assertNull(ReaderPageCache.getChapter(reopened, style, 300, 500, 1f, 1f, false, 1))
    }

    @Test fun `damaged block cache is ignored and can be replaced`() {
        val book = content()
        val pages = ReaderPaginator.paginateChapter(book, 0, charBudget = 200)
        ReaderPageCache.putChapter(book, style, 300, 500, 1f, 1f, true, 0, pages)
        temporary.root.walkTopDown().single { it.name == "0.bin" }.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(ReaderPageCache.getChapter(book, style, 300, 500, 1f, 1f, true, 0))
        ReaderPageCache.putChapter(book, style, 300, 500, 1f, 1f, true, 0, pages)
        assertEquals(4, ReaderPageCache.getChapter(book, style, 300, 500, 1f, 1f, true, 0)?.size)
    }
}
