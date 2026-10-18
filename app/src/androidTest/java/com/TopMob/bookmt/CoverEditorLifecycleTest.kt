package com.TopMob.bookmt

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.core.common.DefaultDispatcherProvider
import com.TopMob.bookmt.data.local.BookMTDatabase
import com.TopMob.bookmt.data.mapper.toEntity
import com.TopMob.bookmt.data.parser.BookParserFactory
import com.TopMob.bookmt.data.repository.BookRepositoryImpl
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.presentation.bookshelf.BookDetailsViewModel
import com.TopMob.bookmt.presentation.navigation.Screen
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoverEditorLifecycleTest {
    @Test fun leavingEditorDeletesUnsavedImportedCoverAndKeepsTheSavedOne() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val root = File.createTempFile("cover-editor-", "", app.cacheDir).apply { delete(); mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir(): File = root }
        val db = Room.inMemoryDatabaseBuilder(app, BookMTDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val covers = File(root, "covers").apply { mkdirs() }
            val savedCover = File(covers, "saved.img").apply { writeText("saved cover") }
            val source = File(root, "source.png")
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { image ->
                image.eraseColor(Color.WHITE)
                source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            val id = db.bookDao().insert(Book(uri = "file:///book.txt", title = "Book", coverImagePath = savedCover.path).toEntity())
            val repository = BookRepositoryImpl(db.bookDao(), BookParserFactory(emptySet()), context.contentResolver,
                DefaultDispatcherProvider(), storageDir = File(root, "books"))
            val imported = CompletableDeferred<String>()
            instrumentation.runOnMainSync {
                val model = BookDetailsViewModel(SavedStateHandle(mapOf(Screen.ARG_BOOK_ID to id)), repository, context)
                store.put("editor", model)
                model.importCover(Uri.fromFile(source)) { imported.complete(it) }
            }
            val importedFile = File(withTimeout(30_000) { imported.await() })
            assertTrue(importedFile.isFile)
            instrumentation.runOnMainSync { store.clear() }
            withTimeout(30_000) { while (importedFile.exists()) delay(20) }
            assertEquals("saved cover", savedCover.readText())
            assertEquals(savedCover.path, db.bookDao().getById(id)!!.coverImagePath)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
            root.deleteRecursively()
        }
    }
}
