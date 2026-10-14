package com.TopMob.bookmt.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
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
    private val storageDir: java.io.File? = null,
) : BookRepository {

    private val offlineMutex = kotlinx.coroutines.sync.Mutex()

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

            val storedUri = storageDir?.let { directory ->
                com.TopMob.bookmt.data.importing.OwnedBookStorage.copy(contentResolver, Uri.parse(uri), directory,
                    parser.format.name.lowercase()).toString()
            } ?: uri
            bookDao.getByUri(storedUri)?.let { return@withContext Resource.Success(it.toDomain()) }
            val metadata = openStream(storedUri).use { parser.parseMetadata(it, fileName) }
            val cover = metadata.coverImageBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
                storageDir?.parentFile?.let { parent ->
                    java.io.File(parent, "covers").apply { mkdirs() }.let { dir ->
                        java.io.File(dir, java.util.UUID.randomUUID().toString() + ".jpg").apply { writeBytes(bytes) }.absolutePath
                    }
                }
            }

            val entity = Book(
                uri = storedUri,
                coverImagePath = cover,
                title = metadata.title,
                author = metadata.author,
                format = parser.format.takeIf { it != BookFormat.UNKNOWN }
                    ?: BookFormat.fromFileName(fileName),
                addedTimestamp = now(),
                fileSizeBytes = if (storedUri.startsWith("file:")) java.io.File(Uri.parse(storedUri).path!!).length() else fileSizeOf(uri),
            ).toEntity()

            val id = bookDao.insert(entity)
            val saved = (bookDao.getById(id) ?: bookDao.getByUri(storedUri))?.toDomain()
                ?: return@withContext Resource.Error(IllegalStateException("Insert failed"))
            Resource.Success(saved)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
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
                val local = prepareOffline(book)
                val content = openStream(local.uri).use { parser.parse(it, book.title) }
                Resource.Success(content)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Resource.Error(t)
            }
        }

    override suspend fun prepareOffline(book: Book): Book = withContext(dispatchers.io) {
        val directory = storageDir ?: return@withContext book
        offlineMutex.withLock {
            val current = bookDao.getById(book.id)?.toDomain() ?: error("Book not found")
            val source = Uri.parse(current.uri)
            val owned = source.scheme == "file" && source.path?.let {
                java.io.File(it).canonicalFile.parentFile == directory.canonicalFile
            } == true
            if (owned) {
                check(java.io.File(checkNotNull(source.path)).isFile) { "Local book file is missing" }
                return@withLock current
            }
            var local = com.TopMob.bookmt.data.importing.OwnedBookStorage.copy(contentResolver, source, directory,
                current.format.name.lowercase())
            val duplicate = bookDao.getByUri(local.toString())
            if (duplicate != null && duplicate.id != current.id) {
                // Keep both legacy records and their annotations when identical files were imported earlier.
                val distinct = java.io.File(directory, "legacy-${current.id}.${current.format.name.lowercase()}")
                java.io.File(checkNotNull(local.path)).copyTo(distinct, overwrite = true)
                local = Uri.fromFile(distinct)
            }
            val size = java.io.File(checkNotNull(local.path)).length()
            bookDao.updateSource(current.id, local.toString(), size)
            current.copy(uri = local.toString(), fileSizeBytes = size)
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

    override suspend fun updateMetadata(
        bookId: Long,
        title: String,
        author: String?,
        description: String?,
        coverImagePath: String?,
        tags: List<String>,
    ) {
        withContext(dispatchers.io) {
            bookDao.updateMetadata(bookId, title, author, description, coverImagePath, tags)
        }
    }

    override suspend fun addReadingTime(bookId: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        withContext(dispatchers.io) { bookDao.addReadingTime(bookId, deltaMs) }
    }

    override suspend fun deleteBook(bookId: Long) {
        withContext(dispatchers.io) {
            val book = bookDao.getById(bookId)
            bookDao.deleteById(bookId)
            val owned = storageDir?.canonicalFile
            if (book != null && owned != null && Uri.parse(book.uri).scheme == "file") {
                val file = Uri.parse(book.uri).path?.let { java.io.File(it).canonicalFile }
                if (file?.parentFile == owned) file.delete()
            }
        }
    }

    private fun openStream(uri: String) =
        contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Cannot open stream for $uri")

    /** Best-effort file size via the SAF metadata column; 0 when the provider doesn't report it. */
    private fun fileSizeOf(uri: String): Long = runCatching {
        contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}
