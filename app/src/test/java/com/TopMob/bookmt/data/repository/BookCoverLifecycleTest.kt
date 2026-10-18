package com.TopMob.bookmt.data.repository

import android.content.ContentResolver
import com.TopMob.bookmt.data.local.dao.BookDao
import com.TopMob.bookmt.data.mapper.toEntity
import com.TopMob.bookmt.data.parser.BookParserFactory
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.util.TestDispatcherProvider
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookCoverLifecycleTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `replacing a cover removes it only after its last reference changes`() = runTest {
        val covers = temporary.newFolder("covers")
        val oldCover = File(covers, "old.img").apply { writeText("old") }
        val rows = mutableMapOf(
            1L to Book(id = 1, uri = "one", title = "One", coverImagePath = oldCover.path).toEntity(),
            2L to Book(id = 2, uri = "two", title = "Two", coverImagePath = oldCover.path).toEntity(),
        )
        val dao = mockk<BookDao>()
        coEvery { dao.getById(any()) } coAnswers { rows[firstArg()] }
        coEvery { dao.countByCoverPath(any()) } coAnswers { rows.values.count { it.coverImagePath == firstArg<String>() } }
        coEvery { dao.updateMetadata(any(), any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            rows[id] = rows.getValue(id).copy(coverImagePath = arg(4))
        }
        val repository = BookRepositoryImpl(dao, mockk<BookParserFactory>(), mockk<ContentResolver>(),
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), storageDir = temporary.newFolder("books"))
        repository.updateMetadata(1, "One", null, null, null, emptyList())
        assertTrue(oldCover.exists())
        repository.updateMetadata(2, "Two", null, null, null, emptyList())
        assertFalse(oldCover.exists())
    }

    @Test fun `failed metadata write keeps the original cover`() = runTest {
        val covers = temporary.newFolder("covers")
        val cover = File(covers, "old.img").apply { writeText("old") }
        val dao = mockk<BookDao>()
        coEvery { dao.getById(1) } returns Book(id = 1, uri = "one", title = "One", coverImagePath = cover.path).toEntity()
        coEvery { dao.updateMetadata(any(), any(), any(), any(), any(), any()) } throws java.io.IOException("write failed")
        val repository = BookRepositoryImpl(dao, mockk<BookParserFactory>(), mockk<ContentResolver>(),
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), storageDir = temporary.newFolder("books"))
        assertTrue(runCatching { repository.updateMetadata(1, "One", null, null, null, emptyList()) }.isFailure)
        assertTrue(cover.exists())
        coVerify(exactly = 0) { dao.countByCoverPath(any()) }
    }
}
