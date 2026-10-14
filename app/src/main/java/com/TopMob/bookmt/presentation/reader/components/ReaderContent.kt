package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.ReadingMode
import com.TopMob.bookmt.presentation.reader.ReaderPage
import com.TopMob.bookmt.presentation.reader.ReaderPaginator
import com.TopMob.bookmt.presentation.reader.ReaderTapZone
import com.TopMob.bookmt.presentation.theme.ReaderColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared measured pagination keeps positions stable across modes, fonts and screen sizes. */
@Composable
fun ReaderContent(
    content: BookContent,
    textStyle: TextStyle,
    colors: ReaderColors,
    mode: ReadingMode,
    horizontalPadding: Int,
    verticalPadding: Int,
    currentOffset: Int,
    navigationVersion: Long,
    onOffsetChanged: (Int) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onRegisterTurn: (((Int) -> Unit)?) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
    bookmarks: List<com.TopMob.bookmt.domain.model.Bookmark> = emptyList(),
    searchResults: List<com.TopMob.bookmt.presentation.reader.BookSearchResult> = emptyList(),
    onSelection: (ReaderSelection) -> Unit = {},
    onRegisterGoToPage: (((Int) -> Unit)?) -> Unit = {},
    onJump: (Int) -> Unit = onOffsetChanged,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val latestOffset by rememberUpdatedState(currentOffset)
    val latestOnOffset by rememberUpdatedState(onOffsetChanged)
    val latestOnPage by rememberUpdatedState(onPageChanged)
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val width = with(density) { (maxWidth - (horizontalPadding * 2).dp).roundToPx() }.coerceAtLeast(1)
        val height = with(density) { (maxHeight - (verticalPadding * 2).dp).roundToPx() }.coerceAtLeast(1)
        // Text color does not affect line wrapping or page boundaries.
        val layoutStyle = remember(textStyle) { textStyle.copy(color = androidx.compose.ui.graphics.Color.Unspecified) }
        var pages by remember(content, layoutStyle, width, height) { mutableStateOf<List<ReaderPage>>(emptyList()) }
        LaunchedEffect(content, layoutStyle, width, height, density.density, density.fontScale) {
            val cached = com.TopMob.bookmt.presentation.reader.ReaderPageCache.get(content, layoutStyle, width, height, density.density, density.fontScale)
            if (cached != null) {
                pages = cached
                return@LaunchedEffect
            }
            pages = withContext(Dispatchers.Default) {
                val jobContext = currentCoroutineContext()
                ReaderPaginator.paginate(content, charBudget = 8000, adaptive = true, checkCancelled = { jobContext.ensureActive() }) { text ->
                    val layout = measurer.measure(text, layoutStyle, constraints = Constraints(maxWidth = width), skipCache = true)
                    var line = 0
                    while (line < layout.lineCount && layout.getLineBottom(line) <= height) line++
                    layout.getLineEnd((line - 1).coerceAtLeast(0), visibleEnd = false)
                }
            }
            com.TopMob.bookmt.presentation.reader.ReaderPageCache.put(content, layoutStyle, width, height, density.density, density.fontScale, pages)
        }
        if (pages.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        key(pages, mode) {
            val start = remember { latestOffset }
            val initialPage = remember { ReaderPaginator.pageIndexForOffset(pages, start) }
            val pager = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
            val list = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
            val scope = rememberCoroutineScope()
            var restoredVersion by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(navigationVersion) {
                restoredVersion = null
                val target = latestOffset
                val index = ReaderPaginator.pageIndexForOffset(pages, target)
                if (mode == ReadingMode.PAGED) {
                    pager.scrollToPage(index)
                } else {
                    val page = pages[index]
                    val layout = measurer.measure(page.text, textStyle, constraints = Constraints(maxWidth = width))
                    val localOffset = (target - page.startOffset).coerceIn(0, page.text.length)
                    val y = if (page.text.isEmpty()) 0 else layout.getLineTop(layout.getLineForOffset(localOffset)).toInt()
                    list.scrollToItem(index, y)
                }
                restoredVersion = navigationVersion
            }
            LaunchedEffect(pages, mode, restoredVersion, navigationVersion) {
                if (restoredVersion != navigationVersion) return@LaunchedEffect
                if (mode == ReadingMode.PAGED) {
                    snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                        latestOnOffset(pages[index].startOffset)
                        latestOnPage(index + 1, pages.size)
                    }
                } else {
                    snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                        .distinctUntilChanged().collect { (index, y) ->
                            val page = pages[index]
                            val layout = measurer.measure(page.text, textStyle, constraints = Constraints(maxWidth = width))
                            val local = if (page.text.isEmpty()) 0 else layout.getLineStart(layout.getLineForVerticalPosition(y.toFloat()))
                            latestOnOffset(page.startOffset + local)
                            latestOnPage(index + 1, pages.size)
                        }
                }
            }
            val turn: (Int) -> Unit = { direction ->
                scope.launch {
                    if (mode == ReadingMode.PAGED) {
                        pager.animateScrollToPage((pager.currentPage + direction).coerceIn(0, pages.lastIndex))
                    } else {
                        list.animateScrollBy(list.layoutInfo.viewportSize.height * direction.toFloat())
                    }
                }
            }
            DisposableEffect(pages, mode) {
                onRegisterTurn(turn)
                onRegisterGoToPage { index -> onJump(pages[index.coerceIn(0, pages.lastIndex)].startOffset) }
                onDispose { onRegisterTurn(null); onRegisterGoToPage(null) }
            }
            val onTap: (Float, Float) -> Unit = { x, widthPx ->
                when (ReaderTapZone.fromX(x, widthPx)) {
                    ReaderTapZone.LEFT -> turn(-1)
                    ReaderTapZone.RIGHT -> turn(1)
                    ReaderTapZone.CENTER -> onToggleControls()
                }
            }
            if (mode == ReadingMode.PAGED) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { index ->
                    ReaderPageText(pages[index], textStyle, bookmarks, searchResults, onSelection, onTap,
                        Modifier.fillMaxSize().padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp))
                }
            } else {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp)) {
                    items(pages.size, key = { pages[it].startOffset }) { index ->
                        ReaderPageText(pages[index], textStyle, bookmarks, searchResults, onSelection, onTap, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
