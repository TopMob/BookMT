package com.TopMob.bookmt.presentation.reader

import android.content.Intent
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.presentation.reader.components.ReaderSelection
import com.TopMob.bookmt.presentation.reader.components.ReaderToolDialogs
import com.TopMob.bookmt.presentation.reader.components.SelectionActionsDialog
import com.TopMob.bookmt.presentation.reader.components.EditAnnotationDialog
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.domain.tts.TtsStatus
import com.TopMob.bookmt.presentation.reader.components.NotesBookmarksPanel
import com.TopMob.bookmt.presentation.reader.components.ReaderBottomBar
import com.TopMob.bookmt.presentation.reader.components.ReaderSideDrawer
import com.TopMob.bookmt.presentation.reader.components.ReaderTopBar
import com.TopMob.bookmt.presentation.reader.components.ReaderContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.TopMob.bookmt.presentation.reader.components.TableOfContentsPanel
import com.TopMob.bookmt.presentation.theme.ReaderColors

@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.book?.format == com.TopMob.bookmt.domain.model.BookFormat.PDF) {
        PdfReaderScreen(onNavigateBack)
        return
    }
    val colors = ReaderColors.forTheme(state.settings.theme)

    ReaderWindowPreferences(state.settings.fullScreen && !state.showControls && state.overlay == ReaderOverlay.NONE,
        state.settings.orientation)
    KeepScreenOn(enabled = state.settings.keepScreenOn)
    BrightnessOverride(
        enabled = state.settings.brightnessOverrideEnabled,
        level = state.settings.brightnessLevel,
    )
    ReadingTimeTracker(
        onResumed = viewModel::onReadingResumed,
        onPaused = viewModel::onReadingPaused,
    )

    var turnPage by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var goToPage by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var selection by remember { mutableStateOf<ReaderSelection?>(null) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val context = LocalContext.current
    var showTtsLicense by remember { mutableStateOf(false) }
    var startAfterLicense by remember { mutableStateOf(false) }
    var requestedNotification by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { viewModel.onPlayTts() }
    fun startSpeech() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !requestedNotification &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestedNotification = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.onPlayTts()
    }
    LaunchedEffect(state.settings.ttsLicenseAccepted, startAfterLicense) {
        if (state.settings.ttsLicenseAccepted && startAfterLicense) {
            startAfterLicense = false
            startSpeech()
        }
    }
    if (showTtsLicense) com.TopMob.bookmt.presentation.settings.TtsLicenseDialog(
        onDismiss = { showTtsLicense = false },
        onAccept = {
            viewModel.onUpdateSettings { it.copy(ttsLicenseAccepted = true) }
            showTtsLicense = false
            startAfterLicense = true
        })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.autoPaging, state.showControls, state.overlay, selection, editing, state.currentPage, state.settings.autoPageSeconds) {
        if (state.autoPaging && !state.showControls && state.overlay == ReaderOverlay.NONE && selection == null && editing == null) {
            if (state.currentPage >= state.totalPages) viewModel.stopAutoPaging()
            else {
                while (true) {
                    kotlinx.coroutines.delay(state.settings.autoPageSeconds * 1000L)
                    turnPage?.invoke(1)
                }
            }
        }
    }
    BackHandler(state.overlay != ReaderOverlay.NONE) { viewModel.onDismissOverlay() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.takeIf { state.content != null }?.let {
            snackbar.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    VolumeKeyScroll(enabled = state.settings.volumeKeysEnabled && state.overlay == ReaderOverlay.NONE && selection == null && editing == null) { key ->
        turnPage?.invoke(if (key == VolumeKey.UP) -1 else 1)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colors.background)
    ) {
        ReaderSurface(
            state = state,
            colors = colors,
            onRegisterTurn = { turnPage = it },
            onRegisterGoToPage = { goToPage = it },
            onSelection = { selection = it },
            onJump = viewModel::jumpToOffset,
            onPageChanged = viewModel::onPageChanged,
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            onScrollOffsetChanged = viewModel::onScrollOffsetChanged,
            onToggleControls = viewModel::onToggleControls,
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
            onSearch = { viewModel.onShowOverlay(ReaderOverlay.SEARCH) },
            onSettings = { viewModel.onShowOverlay(ReaderOverlay.SETTINGS) },
            onGoTo = { viewModel.onShowOverlay(ReaderOverlay.GO_TO) },
            onReturn = if (state.canReturn) viewModel::returnToPreviousPosition else null,
            onAutoPage = viewModel::toggleAutoPaging,
            onShareNotes = {
                val text = buildString {
                    appendLine(state.book?.title.orEmpty())
                    state.bookmarks.sortedBy { it.position }.forEach {
                        appendLine(); appendLine(it.chapterTitle.orEmpty()); appendLine(it.textSnippet)
                        it.note?.let { note -> appendLine(note) }
                    }
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text), null))
            },
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
            ReaderBottomBar(
                visible = state.showControls,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                progress = state.progress,
                showProgressSlider = state.settings.showProgressSlider,
                onSeek = { viewModel.jumpToOffset((it * state.totalLength).toInt()) },
                ttsStatus = state.tts.status,
                activeVoiceId = state.tts.activeVoiceId,
                onPrevChapter = viewModel::onPrevChapter,
                onNextChapter = viewModel::onNextChapter,
                onAddBookmark = viewModel::onAddBookmarkAtCurrentPage,
                onPlayPauseTts = {
                    when (state.tts.status) {
                        TtsStatus.PLAYING, TtsStatus.PREPARING -> viewModel.onPauseTts()
                        TtsStatus.PAUSED -> viewModel.onResumeTts()
                        else -> if (state.settings.ttsLicenseAccepted) startSpeech() else { showTtsLicense = true }
                    }
                },
                onStopTts = viewModel::onStopTts,
            )
        }
        if (state.autoPaging) TextButton(onClick = viewModel::stopAutoPaging,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars)) {
            Text(stringResource(R.string.reader_stop_auto))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        ReaderToolDialogs(state, viewModel) { goToPage?.invoke(it) }
        selection?.let { SelectionActionsDialog(it, { selection = null }, viewModel::onHighlight, viewModel::onAddNote) }
        editing?.let { EditAnnotationDialog(it, viewModel::onUpdateBookmark) { editing = null } }

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
                onEdit = { editing = it },
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
    onRegisterTurn: (((Int) -> Unit)?) -> Unit,
    onRegisterGoToPage: (((Int) -> Unit)?) -> Unit,
    onSelection: (ReaderSelection) -> Unit,
    onJump: (Int) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onScrollOffsetChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
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

        else -> ReaderContent(
            content = content,
            textStyle = state.settings.toTextStyle(colors.text),
            colors = colors,
            horizontalPadding = state.settings.horizontalMarginDp,
            verticalPadding = state.settings.verticalMarginDp,
            mode = state.settings.readingMode,
            currentOffset = state.currentOffset,
            navigationVersion = state.navigationVersion,
            onRegisterTurn = onRegisterTurn,
            onRegisterGoToPage = onRegisterGoToPage,
            onSelection = onSelection,
            onJump = onJump,
            bookmarks = state.bookmarks,
            searchResults = state.searchResults,
            onPageChanged = onPageChanged,
            onOffsetChanged = onScrollOffsetChanged,
            onToggleControls = onToggleControls,
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
    enabled: Boolean,
    onVolumeKey: (VolumeKey) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val latestOnVolumeKey by rememberUpdatedState(onVolumeKey)
    DisposableEffect(activity, enabled) {
        val controller = activity as? VolumeKeyController
        controller?.onVolumeKey = if (enabled) { key -> latestOnVolumeKey(key); true } else null
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
