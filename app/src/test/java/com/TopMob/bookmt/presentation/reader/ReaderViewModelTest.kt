package com.TopMob.bookmt.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.repository.ReaderSettingsRepository
import com.TopMob.bookmt.domain.tts.TtsPlaybackState
import com.TopMob.bookmt.domain.usecase.*
import com.TopMob.bookmt.presentation.navigation.Screen
import com.TopMob.bookmt.util.TestDispatcherProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<BookRepository>(relaxed = true)
    private val bookmarks = mockk<ManageBookmarksUseCase>()
    private val settings = mockk<ReaderSettingsRepository>()
    private val tts = mockk<ReadAloudUseCase>(relaxed = true)
    private val content = BookContent("Book", null, listOf(Chapter(0, "Chapter", "x".repeat(5000), 0)), TableOfContents.EMPTY)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeBook(1) } returns MutableStateFlow(null)
        every { bookmarks.observe(1) } returns MutableStateFlow(emptyList())
        every { settings.settings } returns MutableStateFlow(ReaderSettings())
        every { tts.state } returns MutableStateFlow(TtsPlaybackState())
        coEvery { repository.getBook(1) } returns Book(id = 1, uri = "file:///book.txt", title = "Book", format = BookFormat.TXT, lastReadPosition = 2345)
        coEvery { repository.loadContent(any()) } returns Resource.Success(content)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun reader() = ReaderViewModel(
        SavedStateHandle(mapOf(Screen.ARG_BOOK_ID to 1L)), OpenBookUseCase(repository),
        UpdateReadingProgressUseCase(repository), bookmarks, settings, repository, tts,
        TestDispatcherProvider(dispatcher),
    )

    @Test fun `loads saved position and requests navigation`() = runTest(dispatcher) {
        val vm = reader()
        runCurrent()
        assertEquals(2345, vm.uiState.value.currentOffset)
        assertEquals(1L, vm.uiState.value.navigationVersion)
        coVerify { repository.updateProgress(1, 2345f / 5000, 2345) }
    }

    @Test fun `pausing flushes the latest position before debounce expires`() = runTest(dispatcher) {
        val vm = reader()
        runCurrent()
        vm.onScrollOffsetChanged(3000)
        runCurrent()
        vm.onReadingPaused()
        runCurrent()
        coVerify(exactly = 1) { repository.updateProgress(1, 0.6f, 3000) }
        advanceTimeBy(1000)
        runCurrent()
        coVerify(exactly = 1) { repository.updateProgress(1, 0.6f, 3000) }
    }

    @Test fun `bookmark jumps request navigation even at the same offset`() = runTest(dispatcher) {
        val vm = reader()
        runCurrent()
        vm.jumpToOffset(3456)
        val version = vm.uiState.value.navigationVersion
        vm.jumpToOffset(3456)
        assertEquals(version + 1, vm.uiState.value.navigationVersion)
        assertEquals(3456, vm.uiState.value.currentOffset)
        vm.jumpToOffset(-10)
        assertEquals(0, vm.uiState.value.currentOffset)
        vm.jumpToOffset(Int.MAX_VALUE)
        assertEquals(4999, vm.uiState.value.currentOffset)
    }

    @Test fun `clearing reader flushes pending progress`() = runTest(dispatcher) {
        val vm = reader()
        runCurrent()
        vm.onScrollOffsetChanged(4000)
        val store = ViewModelStore()
        store.put("reader", vm)
        store.clear()
        runCurrent()
        coVerify { repository.updateProgress(1, 0.8f, 4000) }
    }
}
