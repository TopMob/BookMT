package com.TopMob.bookmt.domain.repository

import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the book library. Reactive reads return [Flow]s backed by Room so the
 * bookshelf updates automatically; mutations are suspend one-shots.
 */
interface BookRepository {

    /** Observes the full library. Sorting/filtering is applied in the domain/presentation layer. */
    fun observeBooks(): Flow<List<Book>>

    fun observeBook(bookId: Long): Flow<Book?>

    suspend fun getBook(bookId: Long): Book?

    /**
     * Imports a book from a content URI: extracts metadata, persists a [Book] row, and returns it.
     * Heavy body parsing is deferred until the book is opened ([loadContent]).
     */
    suspend fun importBook(uri: String, fileName: String, mimeType: String?): Resource<Book>

    /** Fully parses a previously-imported book's content for reading. */
    suspend fun loadContent(book: Book): Resource<BookContent>

    suspend fun updateProgress(bookId: Long, progress: Float, position: Int)

    suspend fun updateTags(bookId: Long, tags: List<String>)

    suspend fun deleteBook(bookId: Long)
}
