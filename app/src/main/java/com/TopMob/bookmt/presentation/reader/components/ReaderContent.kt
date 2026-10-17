package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.reader.*
import com.TopMob.bookmt.presentation.theme.ReaderColors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class ScrollPositionState(
    val index: Int,
    val offsetY: Int,
    val isScrolling: Boolean,
    val isAtBottom: Boolean,
)

/** Show the requested block first, then finish a reusable index of page boundaries in the background. */
@Composable
fun ReaderContent(
    content: BookContent, textStyle: TextStyle, colors: ReaderColors, mode: ReadingMode,
    horizontalPadding: Int, verticalPadding: Int, currentOffset: Int, navigationVersion: Long,
    onOffsetChanged: (Int) -> Unit, onPageChanged: (Int, Int) -> Unit,
    onRegisterTurn: (((Int) -> Unit)?) -> Unit, onToggleControls: () -> Unit,
    modifier: Modifier = Modifier, bookmarks: List<Bookmark> = emptyList(),
    searchResults: List<BookSearchResult> = emptyList(), onSelection: (ReaderSelection) -> Unit = {},
    onRegisterGoToPage: (((Int) -> Unit)?) -> Unit = {}, onJump: (Int) -> Unit = onOffsetChanged,
    formatting: Boolean = true, spokenSegment: TextSegment? = null,
    onContentError: (Throwable) -> Unit = {}, onRebuildCache: () -> Unit = {},
    prioritizeAudio: Boolean = false,
    onRegisterLineScroll: (((Int) -> Unit)?) -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val latestOffset by rememberUpdatedState(currentOffset)
    val latestOnOffset by rememberUpdatedState(onOffsetChanged)
    val latestOnPage by rememberUpdatedState(onPageChanged)
    val latestError by rememberUpdatedState(onContentError)
    val audioActive by rememberUpdatedState(prioritizeAudio)
    var link by remember(content) { mutableStateOf<String?>(null) }
    var image by remember(content) { mutableStateOf<Pair<String, String>?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val width = with(density) { (maxWidth - (horizontalPadding * 2).dp).roundToPx() }.coerceAtLeast(1)
        val height = with(density) { (maxHeight - (verticalPadding * 2).dp).roundToPx() }.coerceAtLeast(1)
        val layoutStyle = remember(textStyle) { textStyle.copy(color = androidx.compose.ui.graphics.Color.Unspecified) }
        val blocks = remember(content, layoutStyle, width, height, density.density, density.fontScale, formatting) { ConcurrentHashMap<Int, List<ReaderPage>>() }
        var pages by remember(blocks) { mutableStateOf<List<ReaderPage>>(emptyList()) }
        var complete by remember(blocks) { mutableStateOf(false) }
        var failure by remember(blocks) { mutableStateOf<Throwable?>(null) }
        val pageError: (Throwable) -> Unit = { failure = it; latestError(it) }
        fun measure(page: ReaderPage): Int {
            val rich = page.richText(layoutStyle, width, height, density.density, density.fontScale, formatting)
            val layout = measurer.measure(rich.text, layoutStyle, placeholders = rich.placeholders,
                constraints = Constraints(maxWidth = width), skipCache = true)
            var line = 0
            while (line < layout.lineCount && layout.getLineBottom(line) <= height) line++
            return layout.getLineEnd((line - 1).coerceAtLeast(0), visibleEnd = false)
        }
        suspend fun block(index: Int): List<ReaderPage> {
            blocks[index]?.let { return it }
            val context = currentCoroutineContext()
            val result = ReaderPaginator.paginateChapter(content, index, checkCancelled = { context.ensureActive() }, fit = ::measure)
            return blocks.putIfAbsent(index, result) ?: result
        }
        LaunchedEffect(blocks) {
            try {
                val cached = withContext(Dispatchers.Default) { ReaderPageCache.get(content, layoutStyle, width, height, density.density, density.fontScale, formatting) }
                if (cached != null) { pages = cached; complete = true; return@LaunchedEffect }
                if (content.chapters.isEmpty()) { pages = listOf(ReaderPage("", 0)); complete = true; return@LaunchedEffect }
                val focus = content.chapterIndexAt(latestOffset).coerceAtLeast(0)
                pages = withContext(Dispatchers.Default) { block(focus) }
                val nearby = (focus - 1..focus + 2).filter { it in content.chapters.indices }
                pages = withContext(Dispatchers.Default) { nearby.flatMap { block(it) } }
                val all = withContext(Dispatchers.Default) {
                    val result = ArrayList<ReaderPage>()
                    for (index in content.chapters.indices) {
                        ensureActive()
                        // Background book-wide layout must not compete with speech inference.
                        // Navigation can still measure the requested block independently.
                        while (audioActive) delay(200)
                        result += block(index)
                        yield()
                    }
                    result.ifEmpty { listOf(ReaderPage("", 0)) }
                }
                withContext(Dispatchers.Default) { ReaderPageCache.put(content, layoutStyle, width, height, density.density, density.fontScale, all, formatting) }
                pages = all; complete = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e; latestError(e) }
        }
        // A search/TOC jump remains available while the rest of a large book is being measured.
        LaunchedEffect(navigationVersion, complete) {
            if (!complete && pages.isNotEmpty() && latestOffset !in pages.first().startOffset until pages.last().endOffset) {
                try {
                    val target = content.chapterIndexAt(latestOffset)
                    if (target >= 0) pages = withContext(Dispatchers.Default) { block(target) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure = e; latestError(e) }
            }
        }
        if (failure != null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не удалось прочитать кэш книги. Исходная книга и отметки сохранены.", color = colors.text)
                TextButton(onClick = onRebuildCache) { Text("Пересоздать кэш") }
            }
            return@BoxWithConstraints
        }
        if (pages.isEmpty()) { CircularProgressIndicator(Modifier.align(Alignment.Center)); return@BoxWithConstraints }
        key(mode) {
            val start = remember { latestOffset }
            val initialPage = remember { ReaderPaginator.pageIndexForOffset(pages, start) }
            val pager = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
            val list = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
            val scope = rememberCoroutineScope()
            var restoredVersion by remember { mutableStateOf<Long?>(null) }
            var restoredOffset by remember { mutableIntStateOf(start) }

            // When new chapters are calculated in the background, smoothly adjust list/pager index
            // so the user's scroll position and currently read line are never reset to 0.
            var lastPages by remember { mutableStateOf(pages) }
            LaunchedEffect(pages, restoredVersion, navigationVersion) {
                if (restoredVersion != navigationVersion) {
                    lastPages = pages
                    return@LaunchedEffect
                }
                if (lastPages !== pages && lastPages.isNotEmpty() && pages.isNotEmpty()) {
                    val oldPages = lastPages
                    lastPages = pages
                    if (mode == ReadingMode.PAGED) {
                        val oldPageIndex = pager.currentPage.coerceIn(0, oldPages.lastIndex)
                        val pageOffset = oldPages[oldPageIndex].startOffset
                        val newIndex = ReaderPaginator.pageIndexForOffset(pages, pageOffset)
                        if (newIndex != pager.currentPage) {
                            pager.scrollToPage(newIndex)
                        }
                    } else {
                        val oldItemIndex = list.firstVisibleItemIndex.coerceIn(0, oldPages.lastIndex)
                        val itemScrollOffset = list.firstVisibleItemScrollOffset
                        val pageOffset = oldPages[oldItemIndex].startOffset
                        val newIndex = ReaderPaginator.pageIndexForOffset(pages, pageOffset)
                        if (newIndex != list.firstVisibleItemIndex || itemScrollOffset != list.firstVisibleItemScrollOffset) {
                            list.scrollToItem(newIndex, itemScrollOffset)
                        }
                    }
                } else {
                    lastPages = pages
                }
            }

            LaunchedEffect(navigationVersion) {
                restoredVersion = null
                val target = latestOffset
                val index = ReaderPaginator.pageIndexForOffset(pages, target)
                if (mode == ReadingMode.PAGED) pager.scrollToPage(index) else {
                    val y = withContext(Dispatchers.Default) {
                        val page = pages[index].materialized()
                        val rich = page.richText(textStyle, width, height, density.density, density.fontScale, formatting)
                        val layout = measurer.measure(rich.text, textStyle, placeholders = rich.placeholders, constraints = Constraints(maxWidth = width))
                        val local = (target - page.startOffset).coerceIn(0, page.length)
                        if (page.length == 0) 0 else layout.getLineTop(layout.getLineForOffset(local)).toInt()
                    }
                    list.scrollToItem(index, y)
                }
                restoredOffset = target
                restoredVersion = navigationVersion
            }

            var hasRestoredInitialOffset by remember { mutableStateOf(false) }
            LaunchedEffect(mode, restoredVersion, navigationVersion) {
                if (restoredVersion != navigationVersion) return@LaunchedEffect
                hasRestoredInitialOffset = false
                if (mode == ReadingMode.PAGED) {
                    snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                        if (index in pages.indices) {
                            latestOnOffset(if (!hasRestoredInitialOffset) restoredOffset else pages[index].startOffset)
                            hasRestoredInitialOffset = true
                            latestOnPage(index + 1, if (complete) pages.size else -pages.size)
                        }
                    }
                } else {
                    var lastReportedPageNum = -1
                    var lastReportedOffset = -1
                    snapshotFlow {
                        val firstIndex = list.firstVisibleItemIndex
                        val scrollOffset = list.firstVisibleItemScrollOffset
                        val isScrolling = list.isScrollInProgress
                        val lastItem = list.layoutInfo.visibleItemsInfo.lastOrNull()
                        val isAtBottom = lastItem != null && pages.isNotEmpty() && lastItem.index == pages.lastIndex &&
                            (lastItem.offset + lastItem.size <= list.layoutInfo.viewportEndOffset)
                        ScrollPositionState(firstIndex, scrollOffset, isScrolling, isAtBottom)
                    }.distinctUntilChanged().collectLatest { state ->
                        if (state.index !in pages.indices) return@collectLatest
                        val page = pages[state.index]

                        val pageNum = if (state.isAtBottom) pages.size else (state.index + 1)
                        if (pageNum != lastReportedPageNum) {
                            lastReportedPageNum = pageNum
                            latestOnPage(pageNum, if (complete) pages.size else -pages.size)
                        }

                        if (!hasRestoredInitialOffset) {
                            hasRestoredInitialOffset = true
                            lastReportedOffset = restoredOffset
                            latestOnOffset(restoredOffset)
                            return@collectLatest
                        }

                        // While actively flinging or dragging, debounce the heavy text measurement
                        // so scroll animation remains silky smooth at full display refresh rate.
                        if (state.isScrolling) {
                            delay(120)
                        }

                        val exactOffset = if (page.length == 0 || state.offsetY <= 0) {
                            page.startOffset
                        } else {
                            withContext(Dispatchers.Default) {
                                val snapshot = page.materialized()
                                val rich = snapshot.richText(textStyle, width, height, density.density, density.fontScale, formatting)
                                val layout = measurer.measure(rich.text, textStyle, placeholders = rich.placeholders, constraints = Constraints(maxWidth = width))
                                page.startOffset + layout.getLineStart(layout.getLineForVerticalPosition(state.offsetY.toFloat()))
                            }
                        }

                        if (exactOffset != lastReportedOffset) {
                            lastReportedOffset = exactOffset
                            latestOnOffset(exactOffset)
                        }
                    }
                }
            }
            val turnPaged: (Int) -> Unit = { direction ->
                scope.launch {
                    val maxAdvance = 3
                    val target = (pager.targetPage + direction).coerceIn(
                        pager.currentPage - maxAdvance,
                        pager.currentPage + maxAdvance
                    ).coerceIn(0, pages.lastIndex)
                    pager.animateScrollToPage(target)
                }
            }

            val scrollController = remember(list, scope) {
                object {
                    private var pendingDelta = 0f
                    private var isRunning = false

                    fun scroll(delta: Float, maxPending: Float) {
                        synchronized(this) {
                            pendingDelta = (pendingDelta + delta).coerceIn(-maxPending, maxPending)
                            if (isRunning) return
                            isRunning = true
                        }
                        scope.launch {
                            try {
                                while (true) {
                                    val step: Float
                                    synchronized(this@object) {
                                        step = pendingDelta
                                        pendingDelta = 0f
                                    }
                                    if (kotlin.math.abs(step) < 1f) break
                                    val duration = if (kotlin.math.abs(step) < 250f) 120 else 240
                                    list.animateScrollBy(
                                        value = step,
                                        animationSpec = tween(
                                            durationMillis = duration,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            } finally {
                                synchronized(this@object) {
                                    isRunning = false
                                }
                            }
                        }
                    }
                }
            }

            val turnScroll: (Int) -> Unit = { direction ->
                val viewportHeight = list.layoutInfo.viewportSize.height.toFloat().takeIf { it > 0f } ?: height.toFloat()
                val scrollDelta = (viewportHeight * 0.88f) * direction
                val maxPending = viewportHeight * 2.5f
                scrollController.scroll(scrollDelta, maxPending)
            }

            val scrollLineAction: (Int) -> Unit = { direction ->
                val lineStep = with(density) { 72.dp.toPx() } * direction
                val viewportHeight = list.layoutInfo.viewportSize.height.toFloat().takeIf { it > 0f } ?: height.toFloat()
                val maxPending = viewportHeight * 1.5f
                scrollController.scroll(lineStep, maxPending)
            }

            val turn: (Int) -> Unit = if (mode == ReadingMode.PAGED) turnPaged else turnScroll
            val lineScroll: ((Int) -> Unit)? = if (mode == ReadingMode.SCROLL) scrollLineAction else null

            LaunchedEffect(spokenSegment, audioActive) {
                val segment = spokenSegment ?: return@LaunchedEffect
                if (!audioActive) return@LaunchedEffect
                if (pages.isEmpty()) return@LaunchedEffect
                val targetIndex = ReaderPaginator.pageIndexForOffset(pages, segment.startOffset)
                if (targetIndex !in pages.indices) return@LaunchedEffect

                if (mode == ReadingMode.PAGED) {
                    if (targetIndex != pager.currentPage) {
                        if (kotlin.math.abs(targetIndex - pager.currentPage) <= 1) {
                            pager.animateScrollToPage(targetIndex)
                        } else {
                            pager.scrollToPage(targetIndex)
                        }
                    }
                } else {
                    val visibleInfo = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    if (visibleInfo == null) {
                        list.scrollToItem(targetIndex, 0)
                    } else {
                        val viewportHeight = list.layoutInfo.viewportSize.height.takeIf { it > 0 } ?: height
                        val targetFraction = (segment.startOffset - pages[targetIndex].startOffset).toFloat() / pages[targetIndex].length.coerceAtLeast(1)
                        val approximateLineY = visibleInfo.offset + (visibleInfo.size * targetFraction).toInt()
                        if (approximateLineY > viewportHeight * 0.72f || approximateLineY < 0) {
                            val delta = (approximateLineY - (viewportHeight * 0.30f)).coerceAtLeast(0f)
                            if (delta > 20f && !list.isScrollInProgress) {
                                list.animateScrollBy(delta, tween(durationMillis = 280, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }
            }

            DisposableEffect(pages, mode, complete) {
                onRegisterTurn(turn)
                onRegisterLineScroll(lineScroll)
                onRegisterGoToPage(if (complete) { index -> onJump(pages[index.coerceIn(0, pages.lastIndex)].startOffset) } else null)
                onDispose {
                    onRegisterTurn(null)
                    onRegisterLineScroll(null)
                    onRegisterGoToPage(null)
                }
            }
            val tap: (Float, Float) -> Unit = { x, widthPx -> when (ReaderTapZone.fromX(x, widthPx)) {
                ReaderTapZone.LEFT -> turn(-1); ReaderTapZone.RIGHT -> turn(1); ReaderTapZone.CENTER -> onToggleControls()
            } }
            if (mode == ReadingMode.PAGED) HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { index ->
                ReaderPageText(pages[index], textStyle, bookmarks, searchResults, onSelection, tap,
                    Modifier.fillMaxSize().padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
                    formatting, height, { link = it }, { path, alt -> image = path to alt }, pageError, spokenSegment,
                    colors.ttsHighlight)
            } else LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp)) {
                items(pages.size, key = { pages[it].startOffset }) { index ->
                    ReaderPageText(pages[index], textStyle, bookmarks, searchResults, onSelection, tap, Modifier.fillMaxWidth(),
                        formatting, height, { link = it }, { path, alt -> image = path to alt }, pageError, spokenSegment,
                        colors.ttsHighlight)
                }
            }
        }
    }
    link?.let { target ->
        val offset = content.targets[target]
        val preview by produceState<String?>(null, target) {
            try {
                value = withContext(Dispatchers.IO) {
                    if (offset == null) target else {
                        val next = content.targets.values.filter { it > offset }.minOrNull() ?: content.totalLength
                        val end = minOf(next, offset + 5000)
                        TextAnchors.range(content, offset, end) + if (end < next) "\n…" else ""
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { value = "Не удалось прочитать примечание"; latestError(e) }
        }
        AlertDialog(onDismissRequest = { link = null }, title = { Text(if (offset == null) "Ссылка" else "Примечание") },
            text = { Text(preview ?: "Загрузка…", Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { if (offset != null) TextButton(onClick = { link = null; onJump(offset) }) { Text("Перейти") } },
            dismissButton = { TextButton(onClick = { link = null }) { Text("Закрыть") } })
    }
    image?.let { (path, alt) -> Dialog(onDismissRequest = { image = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember(path) { mutableFloatStateOf(1f) }
        var translation by remember(path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 6f); translation = if (scale > 1f) translation + pan else androidx.compose.ui.geometry.Offset.Zero }
        Surface(Modifier.fillMaxSize()) { Column {
            TextButton(onClick = { image = null }) { Text("Закрыть") }
            AsyncImage(File(path), alt.ifBlank { "Иллюстрация" }, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = translation.x; translationY = translation.y
            }.transformable(transform))
            if (alt.isNotBlank()) Text(alt, Modifier.padding(16.dp))
        } }
    } }
}
