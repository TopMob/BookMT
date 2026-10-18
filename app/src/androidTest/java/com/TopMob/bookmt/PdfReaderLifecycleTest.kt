package com.TopMob.bookmt

import android.graphics.Color
import android.graphics.pdf.PdfDocument
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
import com.TopMob.bookmt.data.repository.BookmarkRepositoryImpl
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.usecase.ManageBookmarksUseCase
import com.TopMob.bookmt.presentation.navigation.Screen
import com.TopMob.bookmt.presentation.reader.PdfReaderViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class PdfReaderLifecycleTest {
    @Test fun progressWriteFailureAndNavigationKeepPublishedImagesUsable() = runBlocking {
        withReader({ throw IOException("Cannot save progress") }) { model ->
            val first = withTimeout(30_000) { model.state.first { it.bitmap != null && it.error != null } }.bitmap!!
            assertFalse(first.isRecycled)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.goTo(1) }
            val second = withTimeout(30_000) {
                model.state.first { it.page == 1 && it.bitmap != null && it.error != null }
            }.bitmap!!
            assertFalse(first.isRecycled)
            assertFalse(second.isRecycled)
            assertEquals(Color.WHITE, first.getPixel(0, 0))
        }
    }

    @Test fun cancellationDuringProgressSaveDoesNotRecycleTheDisplayedImage() = runBlocking {
        val saving = CompletableDeferred<Unit>()
        withReader({ saving.complete(Unit); awaitCancellation() }) { model ->
            withTimeout(30_000) { saving.await() }
            val first = model.state.value.bitmap!!
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.goTo(1) }
            withTimeout(30_000) { model.state.first { it.page == 1 && it.bitmap != null } }
            assertFalse(first.isRecycled)
            assertEquals(Color.WHITE, first.getPixel(0, 0))
        }
    }

    private suspend fun withReader(save: suspend () -> Unit, test: suspend (PdfReaderViewModel) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("pdf-lifecycle-", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val db = Room.inMemoryDatabaseBuilder(context, BookMTDatabase::class.java).build()
        val dispatchers = DefaultDispatcherProvider()
        val repository = BookRepositoryImpl(db.bookDao(), BookParserFactory(emptySet()), context.contentResolver, dispatchers)
        val id = db.bookDao().insert(Book(uri = Uri.fromFile(file).toString(), title = "PDF", format = BookFormat.PDF).toEntity())
        val failing = object : BookRepository by repository {
            override suspend fun updateProgress(bookId: Long, progress: Float, position: Int) { save() }
        }
        val store = ViewModelStore()
        lateinit var model: PdfReaderViewModel
        try {
            instrumentation.runOnMainSync {
                model = PdfReaderViewModel(SavedStateHandle(mapOf(Screen.ARG_BOOK_ID to id)), context, failing,
                    ManageBookmarksUseCase(BookmarkRepositoryImpl(db.bookmarkDao(), dispatchers)), dispatchers)
                store.put("pdf", model)
            }
            test(model)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
            file.delete()
        }
    }
}
