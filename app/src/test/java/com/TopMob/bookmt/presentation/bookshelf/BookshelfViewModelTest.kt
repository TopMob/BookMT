package com.TopMob.bookmt.presentation.bookshelf

import androidx.lifecycle.ViewModelStore
import com.TopMob.bookmt.data.importing.DocumentImport
import com.TopMob.bookmt.data.importing.ImportProgress
import com.TopMob.bookmt.data.settings.LibraryPreferences
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.usecase.GetBooksUseCase
import com.TopMob.bookmt.domain.usecase.ImportBookUseCase
import com.TopMob.bookmt.util.TestDispatcherProvider
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {
    @Test fun `one library subscription serves filtering and continue reading`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        var collection: Job? = null
        try {
            val repository = mockk<BookRepository>()
            val books = MutableStateFlow(listOf(
                Book(id = 1, uri = "one", title = "Completed", progress = .5f, completed = true, lastReadTimestamp = 200),
                Book(id = 2, uri = "two", title = "Reading", progress = .2f, lastReadTimestamp = 100),
            ))
            var subscriptions = 0
            every { repository.observeBooks() } returns flow { subscriptions++; emitAll(books) }
            val preferences = mockk<LibraryPreferences>(relaxed = true)
            every { preferences.query() } returns BookQuery()
            every { preferences.viewMode() } returns BookshelfViewMode.GRID
            val imports = mockk<DocumentImport>()
            every { imports.progress } returns MutableStateFlow<ImportProgress?>(null)
            val dispatchers = TestDispatcherProvider(dispatcher)
            val vm = BookshelfViewModel(GetBooksUseCase(repository, dispatchers), mockk<ImportBookUseCase>(),
                repository, preferences, imports, dispatchers)
            store.put("library", vm)
            collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()
            assertEquals(1, subscriptions)
            assertEquals(2L, vm.uiState.value.continueReadingBook?.id)
            vm.onSearchChange("no matching title")
            runCurrent()
            assertTrue(vm.uiState.value.books.isEmpty())
            assertEquals(2L, vm.uiState.value.continueReadingBook?.id)
            assertEquals(1, subscriptions)
            verify(exactly = 1) { repository.observeBooks() }
        } finally {
            collection?.cancel()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
