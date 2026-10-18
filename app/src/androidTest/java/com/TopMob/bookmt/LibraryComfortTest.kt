package com.TopMob.bookmt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.bookshelf.*
import com.TopMob.bookmt.presentation.settings.*
import com.TopMob.bookmt.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LibraryComfortTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)
    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "ux-library-settings").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun allSettingsCategoriesRemainReachableAtDoubleFontSize() {
        var opened: SettingsPage? = null
        val theme = mutableStateOf(ReaderTheme.DAY)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BookMTTheme(theme.value) {
                    Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { SettingsHome({ opened = it }) } }
                }
            }
        }
        for (page in listOf(SettingsPage.APPEARANCE, SettingsPage.READING, SettingsPage.SPEECH,
            SettingsPage.FILES, SettingsPage.LANGUAGE, SettingsPage.ABOUT)) {
            compose.onNodeWithText(label(page.title)).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(page, opened) }
        }
        for (palette in listOf(ReaderTheme.DAY, ReaderTheme.SEPIA, ReaderTheme.NIGHT, ReaderTheme.AMOLED_BLACK)) {
            compose.runOnIdle { theme.value = palette }
            compose.onNodeWithText(label(SettingsPage.APPEARANCE.title)).performScrollTo()
            capture("settings-${palette.name}-2.0")
        }
    }

    @Test fun thousandBookLibraryKeepsContinueCardInsideScrollableContent() {
        val books = (1L..1000L).map { Book(id = it, uri = "local-$it", title = "Book $it", author = "Author $it", progress = .2f) }
        val state = mutableStateOf(BookshelfUiState(books = books, allBooks = books,
            continueReadingBook = books.first(), isLoading = false, viewMode = BookshelfViewMode.LIST))
        val scale = mutableFloatStateOf(1f)
        val theme = mutableStateOf(ReaderTheme.DAY)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                BookMTTheme(theme.value) {
                    LibraryBrowser(state.value, LibraryCallbacks(view = { state.value = state.value.copy(viewMode = it) }), remember { SnackbarHostState() })
                }
            }
        }
        compose.onNodeWithText(label(R.string.continue_reading)).assertIsDisplayed()
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(40)
        compose.onNodeWithText(label(R.string.continue_reading)).assertDoesNotExist()
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        for (palette in listOf(ReaderTheme.DAY, ReaderTheme.SEPIA, ReaderTheme.NIGHT, ReaderTheme.AMOLED_BLACK)) {
            compose.runOnIdle { theme.value = palette; scale.floatValue = 2f; state.value = state.value.copy(viewMode = BookshelfViewMode.GRID) }
            compose.waitForIdle()
            compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
            compose.onNodeWithText(label(R.string.continue_reading)).assertIsDisplayed()
            capture("library-${palette.name}-2.0")
        }
    }

    @Test fun filterDraftIsDiscardedOnCancelAndAppliedOnlyExplicitly() {
        val books = listOf(Book(id = 1, uri = "one", title = "Reading", progress = .4f),
            Book(id = 2, uri = "two", title = "Untouched"))
        val state = BookshelfUiState(books = books, allBooks = books, isLoading = false)
        val showing = mutableStateOf(true)
        var applied: BookQuery? = null
        compose.setContent { BookMTTheme(ReaderTheme.NIGHT) {
            if (showing.value) LibraryFiltersDialog(state, { applied = it; showing.value = false }, { showing.value = false })
        } }
        compose.onNodeWithText(label(R.string.library_reading)).performClick()
        compose.onNodeWithText(label(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertNull(applied); showing.value = true }
        compose.onNodeWithText(label(R.string.library_reading)).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(context.getString(R.string.library_show_count, 1)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.library_show_count, 1)).performClick()
        compose.runOnIdle { assertEquals(LibraryStatus.READING, applied?.status); assertEquals(LibraryStatus.ALL, state.query.status) }
    }
}
