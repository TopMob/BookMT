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
    private val cacheDir: java.io.File? = null,
    private val bookmarkDao: com.TopMob.bookmt.data.local.dao.BookmarkDao? = null,
    private val database: com.TopMob.bookmt.data.local.BookMTDatabase? = null,
) : BookRepository {

    private data class ContentKey(val uri: String, val bytes: Long, val modified: Long, val title: String, val format: BookFormat)
    private data class CachedContent(val key: ContentKey, val content: BookContent)
    @Volatile private var cachedContent: CachedContent? = null

    private val offlineMutex = kotlinx.coroutines.sync.Mutex()
    private val contentMutex = kotlinx.coroutines.sync.Mutex()

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
                val source = Uri.parse(local.uri)
                val file = if (source.scheme == "file") source.path?.let { java.io.File(it) } else null
                val key = file?.takeIf { it.isFile }?.let { ContentKey(local.uri, it.length(), it.lastModified(), book.title, book.format) }
                cachedContent?.takeIf { key != null && it.key == key }?.let {
                    return@withContext Resource.Success(it.content)
                }
                contentMutex.withLock {
                    val disk = cacheDir?.let { com.TopMob.bookmt.data.reader.DiskBookCache(it) }
                    val diskKey = file?.let { disk?.key(it, book.format) }
                    var content = diskKey?.let { disk?.read(it) }
                    if (content == null) {
                        content = if (disk != null && diskKey != null) disk.writer(diskKey).use { destination ->
                            openStream(local.uri).use { parser.parseTo(it, book.title, destination) }
                        } else openStream(local.uri).use { parser.parse(it, book.title) }
                    }
                    val parsed = checkNotNull(content)
                    val oldMarks = bookmarkDao?.getForBook(book.id).orEmpty()
                    val repaired = if ((local.contentRevision != parsed.revision || oldMarks.any { it.contentRevision != parsed.revision }) && database != null && bookmarkDao != null) {
                        val legacy = if ((local.positionAnchor == null && local.lastReadPosition > 0) || oldMarks.any { it.contentRevision != parsed.revision && (it.startAnchor == null || it.endAnchor == null) }) {
                            try { openStream(local.uri).use { parser.parse(it, book.title) } }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        } else null
                        com.TopMob.bookmt.data.reader.PositionMigration(database).relocate(local, oldMarks, legacy, parsed)
                    } else parsed
                    cachedContent = if (key != null) CachedContent(key, repaired) else null
                    disk?.pin(repaired)
                    if (diskKey != null) disk?.prune(diskKey)
                    Resource.Success(repaired)
                }
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

    override suspend fun saveReadingPosition(bookId: Long, progress: Float, position: Int, anchor: String?, revision: Int) =
        withContext(dispatchers.io) { bookDao.saveReadingPosition(bookId, progress.coerceIn(0f, 1f), position.coerceAtLeast(0), anchor, revision, now()) }
    override suspend fun saveListeningPosition(bookId: Long, position: Int, anchor: String?) =
        withContext(dispatchers.io) { bookDao.saveListeningPosition(bookId, position.coerceAtLeast(0), anchor) }
    override suspend fun setCompleted(bookId: Long, completed: Boolean) = withContext(dispatchers.io) { bookDao.setCompleted(bookId, completed) }
    override suspend fun invalidateContent(bookId: Long) = withContext(dispatchers.io) {
        contentMutex.withLock {
            cachedContent = null
            val book = bookDao.getById(bookId)?.toDomain() ?: return@withLock
            val uri = Uri.parse(book.uri)
            val source = if (uri.scheme == "file") uri.path?.let { java.io.File(it) } else null
            if (source != null && cacheDir != null) {
                val key = com.TopMob.bookmt.data.reader.DiskBookCache(cacheDir).key(source, book.format)
                java.io.File(cacheDir, key).deleteRecursively()
            }
        }
        Unit
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
            if (cachedContent?.key?.uri == book?.uri) cachedContent = null
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
