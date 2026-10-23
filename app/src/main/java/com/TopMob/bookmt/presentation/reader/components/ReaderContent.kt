package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.TopMob.bookmt.R
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class ScrollPositionState(
    val index: Int,
    val offsetY: Int,
    val isScrolling: Boolean,
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
    val motion = com.TopMob.bookmt.presentation.theme.LocalComfortMotion.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val latestOffset by rememberUpdatedState(currentOffset)
    val latestOnOffset by rememberUpdatedState(onOffsetChanged)
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
            val cached = ReaderPageCache.getChapter(content, layoutStyle, width, height, density.density, density.fontScale, formatting, index)
            val result = cached ?: ReaderPaginator.paginateChapter(content, index, checkCancelled = { context.ensureActive() }, fit = ::measure)
            if (cached == null) ReaderPageCache.putChapter(content, layoutStyle, width, height, density.density, density.fontScale, formatting, index, result)
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
            com.TopMob.bookmt.presentation.components.ComfortErrorState(
                message = stringResource(R.string.reader_cache_error), actionLabel = stringResource(R.string.reader_rebuild_cache),
                onAction = onRebuildCache, modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        if (pages.isEmpty()) { com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator(Modifier.align(Alignment.Center)); return@BoxWithConstraints }
        // Event collectors from the previous composition must keep using the
        // same boundaries as their list indices until that composition is replaced.
        val displayedPages = pages
        key(blocks, mode) {
            val start = remember { latestOffset }
            val initialPage = remember { ReaderPaginator.pageIndexForOffset(displayedPages, start) }
            val pager = rememberPagerState(initialPage = initialPage, pageCount = { displayedPages.size })
            val list = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
            val scope = rememberCoroutineScope()
            var restoredVersion by remember { mutableStateOf<Long?>(null) }
            var restoredOffset by remember { mutableIntStateOf(start) }

            val layouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
            var restoredPages by remember { mutableStateOf<List<ReaderPage>?>(null) }
            val ready = restoredVersion == navigationVersion && restoredPages === displayedPages

            // Freeze the requested offset and page list together. Both may change while
            // disk-backed text is loading or the background page index expands.
            LaunchedEffect(displayedPages, navigationVersion) {
                restoredVersion = null
                restoredPages = null
                val target = latestOffset
                val snapshot = displayedPages
                val index = ReaderPaginator.pageIndexForOffset(snapshot, target)
                if (mode == ReadingMode.PAGED) {
                    pager.scrollToPage(index)
                    snapshotFlow { layouts[snapshot[index].startOffset] }.filterNotNull().first()
                } else {
                    list.scrollToItem(index)
                    val page = snapshot[index]
                    val layout = snapshotFlow { layouts[page.startOffset] }.filterNotNull().first()
                    val local = (target - page.startOffset).coerceIn(0, page.length)
                    val y = if (page.length == 0) 0 else kotlin.math.ceil(layout.getLineTop(layout.getLineForOffset(local))).toInt()
                    // onTextLayout precedes placement; wait until the lazy item has its real size.
                    withFrameNanos { }
                    list.scrollToItem(index, y)
                }
                restoredOffset = target
                restoredPages = snapshot
                restoredVersion = navigationVersion
            }

            ReportReaderPages(displayedPages, complete, mode, pager, list, ready, onPageChanged)
            fun visibleOffset(): Int? {
                if (!ready) return null
                val index = if (mode == ReadingMode.PAGED) pager.settledPage else list.firstVisibleItemIndex
                val page = displayedPages.getOrNull(index) ?: return null
                if (mode == ReadingMode.PAGED) return page.startOffset
                val layout = layouts[page.startOffset] ?: return null
                return page.startOffset + layout.getLineStart(
                    layout.getLineForVerticalPosition(list.firstVisibleItemScrollOffset.toFloat()))
            }
            // Use the actual rendered layout: no asynchronous remeasurement/debounce can
            // lose the last visible line when the reader closes during a drag or fling.
            LaunchedEffect(ready, displayedPages, navigationVersion) {
                if (!ready) {
                    return@LaunchedEffect
                }
                latestOnOffset(restoredOffset)
                var initial = true
                snapshotFlow {
                    val viewport = if (mode == ReadingMode.PAGED) ScrollPositionState(pager.settledPage, 0, pager.isScrollInProgress)
                        else ScrollPositionState(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress)
                    viewport to visibleOffset()
                }.distinctUntilChanged().collect { (_, offset) ->
                    if (initial) initial = false else offset?.let(latestOnOffset)
                }
            }
            val latestVisibleOffset by rememberUpdatedState(::visibleOffset)
            DisposableEffect(Unit) {
                onDispose { latestVisibleOffset()?.let(latestOnOffset) }
            }
            val turnPaged: (Int) -> Unit = { direction ->
                scope.launch {
                    val maxAdvance = 3
                    val target = (pager.targetPage + direction).coerceIn(
                        pager.currentPage - maxAdvance,
                        pager.currentPage + maxAdvance
                    ).coerceIn(0, displayedPages.lastIndex)
                    if (motion.spatial) pager.animateScrollToPage(target, animationSpec = tween(160)) else pager.scrollToPage(target)
                }
            }

            val scrollTurns = remember { Channel<Int>(3, BufferOverflow.DROP_OLDEST) }
            LaunchedEffect(list, displayedPages, ready) {
                if (!ready || mode != ReadingMode.SCROLL) {
                    while (scrollTurns.tryReceive().isSuccess) { /* discard stale navigation */ }
                    return@LaunchedEffect
                }
                suspend fun awaitVisibleLayouts() {
                    snapshotFlow {
                        list.layoutInfo.visibleItemsInfo.all { item ->
                            displayedPages.getOrNull(item.index)?.let { layouts[it.startOffset] != null } == true
                        }
                    }.first { it }
                }
                suspend fun alignTopLine() {
                    awaitVisibleLayouts()
                    val index = list.firstVisibleItemIndex
                    val layout = displayedPages.getOrNull(index)?.let { layouts[it.startOffset] } ?: return
                    val line = layout.getLineForVerticalPosition(list.firstVisibleItemScrollOffset.toFloat())
                    list.scrollToItem(index, kotlin.math.ceil(layout.getLineTop(line)).toInt())
                }
                for (direction in scrollTurns) {
                    awaitVisibleLayouts()
                    val info = list.layoutInfo
                    val usableHeight = (info.viewportEndOffset - info.viewportStartOffset -
                        info.beforeContentPadding - info.afterContentPadding).coerceAtLeast(1)
                    if (direction < 0) {
                        if (!list.canScrollBackward) continue
                        list.scrollBy(-usableHeight.toFloat())
                        withFrameNanos { }
                        alignTopLine()
                        continue
                    }
                    if (!list.canScrollForward) continue
                    // Item offsets share the content origin: 0 is the top inner margin,
                    // and viewportEndOffset - afterContentPadding is the bottom inner margin.
                    val bottom = info.viewportEndOffset - info.afterContentPadding
                    var target: Pair<Int, Int>? = null
                    for (item in info.visibleItemsInfo.sortedBy { it.index }) {
                        val layout = layouts[displayedPages[item.index].startOffset] ?: continue
                        val line = (0 until layout.lineCount).firstOrNull {
                            layout.getLineStart(it) < layout.layoutInput.text.length &&
                                item.offset + layout.getLineBottom(it) > bottom + 0.5f
                        }
                        if (line != null) {
                            target = item.index to kotlin.math.ceil(layout.getLineTop(line)).toInt()
                            break
                        }
                    }
                    if (target == null) {
                        val next = (info.visibleItemsInfo.lastOrNull()?.index ?: list.firstVisibleItemIndex) + 1
                        if (next in displayedPages.indices) target = next to 0
                    }
                    val point = target
                    if (point != null && (point.first > list.firstVisibleItemIndex ||
                        point.first == list.firstVisibleItemIndex && point.second > list.firstVisibleItemScrollOffset)) {
                        list.scrollToItem(point.first, point.second)
                    } else {
                        // A single oversized inline image/line must not trap the reader on the same anchor.
                        list.scrollBy(usableHeight.toFloat())
                        withFrameNanos { }
                        awaitVisibleLayouts()
                    }
                    withFrameNanos { }
                }
            }
            val turnScroll: (Int) -> Unit = { direction -> scrollTurns.trySend(if (direction < 0) -1 else 1); Unit }
            val scrollLineAction: (Int) -> Unit = turnScroll

            val turn: (Int) -> Unit = if (mode == ReadingMode.PAGED) turnPaged else turnScroll
            val lineScroll: ((Int) -> Unit)? = if (mode == ReadingMode.SCROLL) scrollLineAction else null

            LaunchedEffect(spokenSegment, audioActive, ready) {
                val segment = spokenSegment ?: return@LaunchedEffect
                if (!audioActive || !ready) return@LaunchedEffect
                if (displayedPages.isEmpty()) return@LaunchedEffect
                val targetIndex = ReaderPaginator.pageIndexForOffset(displayedPages, segment.startOffset)
                if (targetIndex !in displayedPages.indices) return@LaunchedEffect

                if (mode == ReadingMode.PAGED) {
                    if (targetIndex != pager.currentPage) {
                        if (motion.spatial && kotlin.math.abs(targetIndex - pager.currentPage) <= 1) {
                            pager.animateScrollToPage(targetIndex, animationSpec = tween(160))
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
                        val targetFraction = (segment.startOffset - displayedPages[targetIndex].startOffset).toFloat() / displayedPages[targetIndex].length.coerceAtLeast(1)
                        val approximateLineY = visibleInfo.offset + (visibleInfo.size * targetFraction).toInt()
                        if (approximateLineY > viewportHeight * 0.72f || approximateLineY < 0) {
                            val delta = (approximateLineY - (viewportHeight * 0.30f)).coerceAtLeast(0f)
                            if (delta > 20f && !list.isScrollInProgress) {
                                if (motion.spatial) list.animateScrollBy(delta, tween(durationMillis = 160, easing = FastOutSlowInEasing)) else list.scrollBy(delta)
                            }
                        }
                    }
                }
            }

            DisposableEffect(displayedPages, mode, complete, ready) {
                onRegisterTurn(if (ready) turn else null)
                onRegisterLineScroll(if (ready) lineScroll else null)
                onRegisterGoToPage(if (complete && ready) { index -> onJump(displayedPages[index.coerceIn(0, displayedPages.lastIndex)].startOffset) } else null)
                onDispose {
                    onRegisterTurn(null)
                    onRegisterLineScroll(null)
                    onRegisterGoToPage(null)
                }
            }
            val tap: (Float, Float) -> Unit = { x, widthPx -> if (ready) when (ReaderTapZone.fromX(x, widthPx)) {
                ReaderTapZone.LEFT -> turn(-1); ReaderTapZone.RIGHT -> turn(1); ReaderTapZone.CENTER -> onToggleControls()
            } }
            if (mode == ReadingMode.PAGED) HorizontalPager(pager, userScrollEnabled = ready, modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f)) { index ->
                val page = displayedPages.getOrNull(index) ?: return@HorizontalPager
                ReaderPageText(page, textStyle, bookmarks, searchResults, onSelection, tap,
                    Modifier.fillMaxSize().padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
                    formatting, height, { link = it }, { path, alt -> image = path to alt }, pageError, spokenSegment,
                    colors.ttsHighlight, { result -> if (result == null) layouts.remove(page.startOffset) else layouts[page.startOffset] = result })
            } else LazyColumn(state = list, userScrollEnabled = ready, modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f),
                contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp)) {
                items(displayedPages.size, key = { displayedPages.getOrNull(it)?.startOffset ?: it }) { index ->
                    val page = displayedPages.getOrNull(index) ?: return@items
                    ReaderPageText(page, textStyle, bookmarks, searchResults, onSelection, tap, Modifier.fillMaxWidth(),
                        formatting, height, { link = it }, { path, alt -> image = path to alt }, pageError, spokenSegment,
                        colors.ttsHighlight, { result -> if (result == null) layouts.remove(page.startOffset) else layouts[page.startOffset] = result })
                }
            }
            if (!ready) com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator(Modifier.align(Alignment.Center))
        }
    }
    link?.let { target ->
        val offset = content.targets[target]
        val footnoteError = stringResource(R.string.reader_footnote_error)
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
            catch (e: Exception) { value = footnoteError; latestError(e) }
        }
        AlertDialog(onDismissRequest = { link = null },
            title = { Text(stringResource(if (offset == null) R.string.reader_link else R.string.reader_footnote)) },
            text = { Text(preview ?: stringResource(R.string.book_details_loading), Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { if (offset != null) TextButton(onClick = { link = null; onJump(offset) }) { Text(stringResource(R.string.reader_go_to)) } },
            dismissButton = { TextButton(onClick = { link = null }) { Text(stringResource(R.string.action_close)) } })
    }
    image?.let { (path, alt) -> Dialog(onDismissRequest = { image = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember(path) { mutableFloatStateOf(1f) }
        var translation by remember(path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 6f); translation = if (scale > 1f) translation + pan else androidx.compose.ui.geometry.Offset.Zero }
        Surface(Modifier.fillMaxSize()) { Column {
            TextButton(onClick = { image = null }) { Text(stringResource(R.string.action_close)) }
            AsyncImage(File(path), alt.ifBlank { stringResource(R.string.reader_image) }, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = translation.x; translationY = translation.y
            }.transformable(transform))
            if (alt.isNotBlank()) Text(alt, Modifier.padding(16.dp))
        } }
    } }
}
