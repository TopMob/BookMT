package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetBooksUseCaseTest {

    private val sample = listOf(
        Book(id = 1, uri = "a", title = "Bravo", author = "Zed", progress = 0.5f, addedTimestamp = 100),
        Book(id = 2, uri = "b", title = "Alpha", author = "Ann", progress = 1.0f, completed = true, addedTimestamp = 300),
        Book(id = 3, uri = "c", title = "Charlie", author = "Max", progress = 0.0f, addedTimestamp = 200),
    )

    private val useCase = GetBooksUseCase(FakeBookRepository(sample))

    @Test
    fun `sort by title ascending`() = runTest {
        val result = useCase(BookQuery(sortOrder = BookSortOrder.TITLE, ascending = true)).first()
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), result.map { it.title })
    }

    @Test
    fun `only in progress filters out finished and untouched`() = runTest {
        val result = useCase(BookQuery(onlyInProgress = true)).first()
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `search matches title or author case-insensitively`() = runTest {
        val result = useCase(BookQuery(searchQuery = "ann")).first()
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `completed books cannot also be unread or in progress`() = runTest {
        val books = listOf(
            Book(id = 1, uri = "one", title = "Finished", completed = true, progress = 0.4f),
            Book(id = 2, uri = "two", title = "Finished at start", completed = true, progress = 0f),
            Book(id = 3, uri = "three", title = "Reading", progress = 0.2f),
            Book(id = 4, uri = "four", title = "Untouched", progress = 0f),
        )
        val filter = GetBooksUseCase(FakeBookRepository(books))
        assertEquals(setOf(1L, 2L), filter(BookQuery(status = com.TopMob.bookmt.domain.model.LibraryStatus.FINISHED)).first().map { it.id }.toSet())
        assertEquals(listOf(3L), filter(BookQuery(status = com.TopMob.bookmt.domain.model.LibraryStatus.READING)).first().map { it.id })
        assertEquals(listOf(4L), filter(BookQuery(status = com.TopMob.bookmt.domain.model.LibraryStatus.UNREAD)).first().map { it.id })
        assertEquals(listOf(3L), filter(BookQuery(onlyInProgress = true)).first().map { it.id })
    }


    @Test fun `removing finished flag at end retains progress and returns to reading`() = runTest {
        val book = Book(id = 8, uri = "local", title = "Book", progress = 1f, completed = false, lastReadPosition = 420)
        val filter = GetBooksUseCase(FakeBookRepository(listOf(book)))
        assertEquals(emptyList<Book>(), filter(BookQuery(status = com.TopMob.bookmt.domain.model.LibraryStatus.FINISHED)).first())
        assertEquals(listOf(book), filter(BookQuery(status = com.TopMob.bookmt.domain.model.LibraryStatus.READING)).first())
    }

    @Test fun `equal sort values have stable id order despite repository order`() = runTest {
        val books = listOf(9L, 2L, 7L).map { Book(id = it, uri = "$it", title = "Same") }
        val filter = GetBooksUseCase(FakeBookRepository(books))
        assertEquals(listOf(2L, 7L, 9L), filter(BookQuery(sortOrder = BookSortOrder.TITLE, ascending = true)).first().map { it.id })
    }

    private class FakeBookRepository(private val books: List<Book>) : BookRepository {
        override fun observeBooks(): Flow<List<Book>> = flowOf(books)
        override fun observeBook(bookId: Long): Flow<Book?> = flowOf(books.find { it.id == bookId })
        override suspend fun getBook(bookId: Long): Book? = books.find { it.id == bookId }
        override suspend fun importBook(uri: String, fileName: String, mimeType: String?): Resource<Book> =
            Resource.Error(NotImplementedError())
        override suspend fun loadContent(book: Book): Resource<BookContent> =
            Resource.Error(NotImplementedError())
        override suspend fun updateProgress(bookId: Long, progress: Float, position: Int) = Unit
        override suspend fun updateTags(bookId: Long, tags: List<String>) = Unit
        override suspend fun deleteBook(bookId: Long) = Unit
        override suspend fun addReadingTime(bookId: Long, deltaMs: Long) = Unit
        override suspend fun updateMetadata(bookId: Long, title: String, author: String?, description: String?, coverImagePath: String?, tags: List<String>) = Unit
    }
}
