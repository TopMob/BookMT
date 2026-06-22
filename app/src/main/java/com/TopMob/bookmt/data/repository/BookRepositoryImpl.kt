package com.TopMob.bookmt.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.data.local.dao.BookDao
import com.TopMob.bookmt.data.mapper.toDomain
import com.TopMob.bookmt.data.mapper.toEntity
import com.TopMob.bookmt.data.parser.BookParserFactory
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Default [BookRepository]. Bridges Room (library metadata) and the parser layer (book content),
 * and resolves file content through the platform [ContentResolver] so it works with SAF URIs.
 *
 * Time is injected as a lambda so the repository is unit-testable without touching the system clock.
 */
class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val parserFactory: BookParserFactory,
    private val contentResolver: ContentResolver,
    private val dispatchers: DispatcherProvider,
    private val now: () -> Long = System::currentTimeMillis,
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBook(bookId: Long): Flow<Book?> =
        bookDao.observeById(bookId).map { it?.toDomain() }

    override suspend fun getBook(bookId: Long): Book? =
        withContext(dispatchers.io) { bookDao.getById(bookId)?.toDomain() }

    override suspend fun importBook(
        uri: String,
        fileName: String,
        mimeType: String?,
    ): Resource<Book> = withContext(dispatchers.io) {
        try {
            // Avoid duplicates: a book already imported from this URI is returned as-is.
            bookDao.getByUri(uri)?.let { return@withContext Resource.Success(it.toDomain()) }

            val parser = parserFactory.parserFor(fileName, mimeType)
                ?: return@withContext Resource.Error(
                    IllegalArgumentException("Unsupported format for $fileName"),
                )

            val metadata = openStream(uri).use { parser.parseMetadata(it, fileName) }

            val entity = Book(
                uri = uri,
                title = metadata.title,
                author = metadata.author,
                format = parser.format.takeIf { it != BookFormat.UNKNOWN }
                    ?: BookFormat.fromFileName(fileName),
                addedTimestamp = now(),
            ).toEntity()

            val id = bookDao.insert(entity)
            val saved = (bookDao.getById(id) ?: bookDao.getByUri(uri))?.toDomain()
                ?: return@withContext Resource.Error(IllegalStateException("Insert failed"))
            Resource.Success(saved)
        } catch (t: Throwable) {
            Resource.Error(t)
        }
    }

    override suspend fun loadContent(book: Book): Resource<BookContent> =
        withContext(dispatchers.io) {
            try {
                val parser = parserFactory.parserFor(book.format)
                    ?: parserFactory.parserFor(book.uri, null)
                    ?: return@withContext Resource.Error(
                        IllegalStateException("No parser for ${book.format}"),
                    )
                val content = openStream(book.uri).use { parser.parse(it, book.title) }
                Resource.Success(content)
            } catch (t: Throwable) {
                Resource.Error(t)
            }
        }

    override suspend fun updateProgress(bookId: Long, progress: Float, position: Int) {
        withContext(dispatchers.io) {
            bookDao.updateProgress(bookId, progress, position, now())
        }
    }

    override suspend fun updateTags(bookId: Long, tags: List<String>) {
        withContext(dispatchers.io) { bookDao.updateTags(bookId, tags) }
    }

    override suspend fun deleteBook(bookId: Long) {
        withContext(dispatchers.io) { bookDao.deleteById(bookId) }
    }

    private fun openStream(uri: String) =
        contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Cannot open stream for $uri")
}
