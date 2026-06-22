package com.TopMob.bookmt.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.domain.tts.TtsStatus
import com.TopMob.bookmt.presentation.reader.components.NotesBookmarksPanel
import com.TopMob.bookmt.presentation.reader.components.ReaderBottomBar
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
    val context = LocalContext.current

    KeepScreenOn(enabled = state.settings.keepScreenOn)
    BrightnessOverride(
        enabled = state.settings.brightnessOverrideEnabled,
        level = state.settings.brightnessLevel,
    )
    ReadingTimeTracker(
        onResumed = viewModel::onReadingResumed,
        onPaused = viewModel::onReadingPaused,
    )

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    VolumeKeyScroll { key ->
        val scrollAmount = listState.layoutInfo.viewportSize.height * 1.0f
        if (scrollAmount > 0) {
            scope.launch {
                if (key == VolumeKey.UP) listState.animateScrollBy(-scrollAmount)
                else listState.animateScrollBy(scrollAmount)
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colors.background)
    ) {
        ReaderSurface(
            state = state,
            colors = colors,
            listState = listState,
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            onScrollOffsetChanged = viewModel::onScrollOffsetChanged,
            onTapZone = { zone ->
                when (zone) {
                    ReaderTapZone.LEFT -> {
                        val scrollAmount = listState.layoutInfo.viewportSize.height * 1.0f
                        if (scrollAmount > 0) scope.launch { listState.animateScrollBy(-scrollAmount) }
                    }
                    ReaderTapZone.RIGHT -> {
                        val scrollAmount = listState.layoutInfo.viewportSize.height * 1.0f
                        if (scrollAmount > 0) scope.launch { listState.animateScrollBy(scrollAmount) }
                    }
                    ReaderTapZone.CENTER -> viewModel.onToggleControls()
                }
            },
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
            onToggleTheme = {
                val nextTheme = when (state.settings.theme) {
                    com.TopMob.bookmt.domain.model.ReaderTheme.DAY -> com.TopMob.bookmt.domain.model.ReaderTheme.NIGHT
                    com.TopMob.bookmt.domain.model.ReaderTheme.NIGHT -> com.TopMob.bookmt.domain.model.ReaderTheme.AMOLED_BLACK
                    else -> com.TopMob.bookmt.domain.model.ReaderTheme.DAY
                }
                viewModel.onUpdateSettings { it.copy(theme = nextTheme) }
            },
        )

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            val charsPerPage = 1500
            val fakeCurrentPage = (state.currentOffset / charsPerPage) + 1
            val fakeTotalPages = (state.totalLength / charsPerPage) + 1

            ReaderBottomBar(
                visible = state.showControls,
                currentPage = fakeCurrentPage,
                totalPages = fakeTotalPages,
                ttsStatus = state.tts.status,
                activeVoiceId = state.tts.activeVoiceId,
                onPrevChapter = viewModel::onPrevChapter,
                onNextChapter = viewModel::onNextChapter,
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
}

/** Mirrors the reader's foreground lifetime to the view-model so it can accrue reading time. */
@Composable
private fun ReadingTimeTracker(onResumed: () -> Unit, onPaused: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResumed()
                Lifecycle.Event.ON_PAUSE -> onPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onPaused()
        }
    }
}

@Composable
private fun ReaderSurface(
    state: ReaderUiState,
    colors: ReaderColors,
    listState: LazyListState,
    onScrollOffsetChanged: (Int) -> Unit,
    onTapZone: (ReaderTapZone) -> Unit,
    modifier: Modifier = Modifier,
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

        else -> ScrollReaderContent(
            content = content,
            textStyle = state.settings.toTextStyle(colors.text),
            colors = colors,
            horizontalPadding = state.settings.horizontalMarginDp,
            verticalPadding = state.settings.verticalMarginDp,
            listState = listState,
            onOffsetChanged = onScrollOffsetChanged,
            onTapZone = onTapZone,
            modifier = modifier,
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
private fun VolumeKeyScroll(
    onVolumeKey: (VolumeKey) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val latestOnVolumeKey by rememberUpdatedState(onVolumeKey)
    DisposableEffect(activity) {
        val controller = activity as? VolumeKeyController
        controller?.onVolumeKey = { key ->
            latestOnVolumeKey(key)
            true
        }
        onDispose { controller?.onVolumeKey = null }
    }
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
