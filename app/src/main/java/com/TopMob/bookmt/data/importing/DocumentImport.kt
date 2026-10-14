package com.TopMob.bookmt.data.importing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.usecase.ImportBookUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ImportBatch(val bookIds: List<Long>, val failures: List<String>)

data class ImportProgress(val completed: Int, val total: Int)

@Singleton
class DocumentImport @Inject constructor(@ApplicationContext private val context: Context, private val importBook: ImportBookUseCase,
    private val books: com.TopMob.bookmt.domain.repository.BookRepository) {
    private val mutex = Mutex()
    private val current = MutableStateFlow<ImportProgress?>(null)
    val progress = current.asStateFlow()

    suspend fun prepareLibrary(): List<String> = mutex.withLock {
        val library = books.observeBooks().first()
        val failures = mutableListOf<String>()
        try {
            library.forEachIndexed { index, book ->
                current.value = ImportProgress(index, library.size)
                try { books.prepareOffline(book) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failures += "${book.title}: ${e.message}" }
                current.value = ImportProgress(index + 1, library.size)
            }
        } finally { current.value = null }
        failures
    }

    suspend fun import(uris: List<Uri>): ImportBatch = mutex.withLock {
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            val bookIds = mutableListOf<Long>()
            val unique = uris.distinct()
            try {
                unique.forEachIndexed { index, uri ->
                    current.value = ImportProgress(index, unique.size)
                    try {
                        val resolver = context.contentResolver
                        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: uri.lastPathSegment.orEmpty()
                        val result = importBook(uri.toString(), name, resolver.getType(uri))
                        if (result is Resource.Error) failures += "$name: ${result.message.orEmpty()}"
                        if (result is Resource.Success) bookIds += result.data.id
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures += "${uri.lastPathSegment}: ${e.message}" }
                    current.value = ImportProgress(index + 1, unique.size)
                }
            } finally { current.value = null }
            ImportBatch(bookIds, failures)
        }
    }
}
