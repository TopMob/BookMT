package com.TopMob.bookmt.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.domain.model.ReadingMode
import com.TopMob.bookmt.domain.tts.TtsStatus
import com.TopMob.bookmt.presentation.reader.components.NotesBookmarksPanel
import com.TopMob.bookmt.presentation.reader.components.PagedReaderContent
import com.TopMob.bookmt.presentation.reader.components.ReaderBottomBar
import com.TopMob.bookmt.presentation.reader.components.ReaderSettingsSheet
import com.TopMob.bookmt.presentation.reader.components.ReaderSideDrawer
import com.TopMob.bookmt.presentation.reader.components.ReaderTopBar
import com.TopMob.bookmt.presentation.reader.components.ScrollReaderContent
import com.TopMob.bookmt.presentation.reader.components.TableOfContentsPanel
import com.TopMob.bookmt.presentation.theme.ReaderColors

@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ReaderColors.forTheme(state.settings.theme)

    KeepScreenOn(enabled = state.settings.keepScreenOn)
    BrightnessOverride(
        enabled = state.settings.brightnessOverrideEnabled,
        level = state.settings.brightnessLevel,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ReaderSurface(
            state = state,
            colors = colors,
            onPageSettled = viewModel::onPageChanged,
            onScrollOffsetChanged = viewModel::onScrollOffsetChanged,
            onTapZone = { zone ->
                when (zone) {
                    ReaderTapZone.LEFT ->
                        viewModel.onPageChanged((state.currentPageIndex - 1).coerceAtLeast(0))
                    ReaderTapZone.RIGHT ->
                        viewModel.onPageChanged(
                            (state.currentPageIndex + 1).coerceAtMost(state.pages.lastIndex.coerceAtLeast(0)),
                        )
                    ReaderTapZone.CENTER -> viewModel.onToggleControls()
                }
            },
            onScrollTap = viewModel::onToggleControls,
        )

        // Left-edge vertical drag adjusts brightness when the override is enabled.
        if (state.settings.brightnessOverrideEnabled) {
            BrightnessEdgeStrip(
                level = state.settings.brightnessLevel,
                onLevelChange = { newLevel ->
                    viewModel.onUpdateSettings { it.copy(brightnessLevel = newLevel) }
                },
            )
        }

        ReaderTopBar(
            title = state.currentChapterTitle ?: state.book?.title.orEmpty(),
            visible = state.showControls,
            onBack = onNavigateBack,
            onOpenToc = { viewModel.onShowOverlay(ReaderOverlay.TABLE_OF_CONTENTS) },
            onOpenNotes = { viewModel.onShowOverlay(ReaderOverlay.NOTES) },
            onOpenSettings = { viewModel.onShowOverlay(ReaderOverlay.SETTINGS) },
        )

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(
                visible = state.showControls,
                progress = state.progress,
                ttsStatus = state.tts.status,
                activeVoiceId = state.tts.activeVoiceId,
                onSeek = viewModel::onSeek,
                onAddBookmark = viewModel::onAddBookmarkAtCurrentPage,
                onPlayPauseTts = {
                    when (state.tts.status) {
                        TtsStatus.PLAYING -> viewModel.onPauseTts()
                        TtsStatus.PAUSED -> viewModel.onResumeTts()
                        else -> viewModel.onPlayTts()
                    }
                },
                onStopTts = viewModel::onStopTts,
            )
        }

        // Notes & Bookmarks side drawer
        ReaderSideDrawer(
            visible = state.overlay == ReaderOverlay.NOTES,
            onDismiss = viewModel::onDismissOverlay,
        ) {
            NotesBookmarksPanel(
                bookmarks = state.bookmarks,
                onEntryClick = {
                    viewModel.jumpToOffset(it.position)
                    viewModel.onDismissOverlay()
                },
                onDelete = viewModel::onDeleteBookmark,
            )
        }

        // Table of contents side drawer
        ReaderSideDrawer(
            visible = state.overlay == ReaderOverlay.TABLE_OF_CONTENTS,
            onDismiss = viewModel::onDismissOverlay,
        ) {
            TableOfContentsPanel(
                entries = state.content?.tableOfContents?.entries.orEmpty(),
                onEntryClick = {
                    viewModel.jumpToOffset(it.offset)
                    viewModel.onDismissOverlay()
                },
            )
        }
    }

    if (state.overlay == ReaderOverlay.SETTINGS) {
        ReaderSettingsSheet(
            settings = state.settings,
            onUpdate = viewModel::onUpdateSettings,
            onDismiss = viewModel::onDismissOverlay,
        )
    }
}

@Composable
private fun ReaderSurface(
    state: ReaderUiState,
    colors: ReaderColors,
    onPageSettled: (Int) -> Unit,
    onScrollOffsetChanged: (Int) -> Unit,
    onTapZone: (ReaderTapZone) -> Unit,
    onScrollTap: () -> Unit,
) {
    val content = state.content
    when {
        state.isLoading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        content == null -> Box(Modifier.fillMaxSize()) {
            Text(
                text = state.errorMessage ?: "Unable to load book",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        state.settings.readingMode == ReadingMode.PAGED -> PagedReaderContent(
            pages = state.pages,
            currentPageIndex = state.currentPageIndex,
            textStyle = state.settings.toTextStyle(colors.text),
            colors = colors,
            horizontalPadding = state.settings.horizontalMarginDp,
            verticalPadding = state.settings.verticalMarginDp,
            onPageSettled = onPageSettled,
            onTapZone = onTapZone,
        )

        else -> ScrollReaderContent(
            content = content,
            textStyle = state.settings.toTextStyle(colors.text),
            colors = colors,
            horizontalPadding = state.settings.horizontalMarginDp,
            verticalPadding = state.settings.verticalMarginDp,
            onOffsetChanged = onScrollOffsetChanged,
            onTap = onScrollTap,
        )
    }
}

/** Thin left-edge gesture region: dragging up brightens, down dims (relative to height). */
@Composable
private fun BrightnessEdgeStrip(level: Float, onLevelChange: (Float) -> Unit) {
    // rememberUpdatedState so the long-lived pointerInput lambda always sees the latest level.
    val currentLevel by rememberUpdatedState(level)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(32.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Drag up (negative dy) increases brightness.
                    val delta = -dragAmount / size.height
                    onLevelChange((currentLevel + delta).coerceIn(0.01f, 1f))
                }
            },
    )
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun BrightnessOverride(enabled: Boolean, level: Float) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, level) {
        val window = activity?.window
        val attrs = window?.attributes
        val original = attrs?.screenBrightness
        if (window != null && attrs != null && enabled) {
            attrs.screenBrightness = level.coerceIn(0.01f, 1f)
            window.attributes = attrs
        }
        onDispose {
            if (window != null && attrs != null && original != null) {
                attrs.screenBrightness = original
                window.attributes = attrs
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
