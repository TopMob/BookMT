package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.presentation.reader.HighlightAnnotator
import com.TopMob.bookmt.presentation.reader.Page
import com.TopMob.bookmt.presentation.reader.ReaderTapZone
import com.TopMob.bookmt.presentation.theme.ReaderColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Paged reading surface that provides horizontal swipe gestures and realistic page turn animations.
 *
 * Each page is sliced at word boundaries with a bottom padding buffer to guarantee
 * lines are never cut mid-row ("зажевать полстрочки").
 */
@Composable
fun PagedReaderContent(
    pages: List<Page>,
    currentPageIndex: Int,
    textStyle: TextStyle,
    colors: ReaderColors,
    horizontalPadding: Int,
    verticalPadding: Int,
    showStatusBar: Boolean = true,
    chapterTitle: String? = null,
    minutesRemaining: Int? = null,
    bookmarks: List<Bookmark> = emptyList(),
    onOffsetChanged: (Int) -> Unit,
    onTapZone: (ReaderTapZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(colors.background))
        return
    }

    val validInitial = currentPageIndex.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = validInitial,
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()

    // Sync external jumps (TOC, bookmarks) to pager
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex in pages.indices && pagerState.currentPage != currentPageIndex) {
            pagerState.scrollToPage(currentPageIndex)
        }
    }

    // Report page changes from user gestures to update persistent reading progress
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                pages.getOrNull(pageIndex)?.let { page ->
                    onOffsetChanged(page.startOffset)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages.size) {
                    detectTapGestures { offset ->
                        val zone = ReaderTapZone.fromX(offset.x, size.width.toFloat())
                        when (zone) {
                            ReaderTapZone.LEFT -> {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            }
                            ReaderTapZone.RIGHT -> {
                                if (pagerState.currentPage < pages.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            }
                            ReaderTapZone.CENTER -> onTapZone(ReaderTapZone.CENTER)
                        }
                    }
                },
        ) { pageIndex ->
            val page = pages.getOrNull(pageIndex)
            val topPad = if (showStatusBar) (verticalPadding + 20).dp else verticalPadding.dp
            val botPad = if (showStatusBar) (verticalPadding + 28).dp else (verticalPadding + 12).dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Realistic page-turning depth & alpha curve
                        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        if (pageOffset > 0f) {
                            // Turning page away to the left
                            alpha = (1f - pageOffset * 0.35f).coerceIn(0.5f, 1f)
                            scaleX = (1f - pageOffset * 0.05f).coerceIn(0.95f, 1f)
                            scaleY = (1f - pageOffset * 0.05f).coerceIn(0.95f, 1f)
                        } else if (pageOffset < 0f) {
                            // Incoming page from right
                            val absOffset = pageOffset.absoluteValue
                            alpha = (1f - absOffset * 0.2f).coerceIn(0.8f, 1f)
                        }
                    }
                    .padding(
                        start = horizontalPadding.dp,
                        end = horizontalPadding.dp,
                        top = topPad,
                        bottom = botPad,
                    )
            ) {
                if (page != null) {
                    val annotatedText = remember(page.text, page.startOffset, page.endOffset, bookmarks, colors.highlightDefault) {
                        HighlightAnnotator.annotate(
                            text = page.text,
                            startOffset = page.startOffset,
                            endOffset = page.endOffset,
                            bookmarks = bookmarks,
                            defaultHighlightColor = colors.highlightDefault,
                        )
                    }
                    Text(
                        text = annotatedText,
                        style = textStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (showStatusBar) {
            ReaderTopStatusBar(
                chapterTitle = chapterTitle,
                colors = colors,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
            val curPage = (pagerState.currentPage + 1).coerceAtMost(pages.size)
            val pct = if (pages.isNotEmpty()) ((curPage.toFloat() / pages.size) * 100).toInt() else 0
            ReaderBottomStatusBar(
                currentPage = curPage,
                totalPages = pages.size,
                progressPercent = pct,
                minutesRemaining = minutesRemaining,
                colors = colors,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * Continuous vertical-scroll reading surface. Each chapter is one lazy item.
 */
@Composable
fun ScrollReaderContent(
    content: BookContent,
    textStyle: TextStyle,
    colors: ReaderColors,
    horizontalPadding: Int,
    verticalPadding: Int,
    listState: LazyListState,
    showStatusBar: Boolean = true,
    chapterTitle: String? = null,
    minutesRemaining: Int? = null,
    bookmarks: List<Bookmark> = emptyList(),
    onOffsetChanged: (Int) -> Unit,
    onTapZone: (ReaderTapZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                content.chapters.getOrNull(index)?.let { onOffsetChanged(it.startOffset) }
            }
    }

    val topPad = if (showStatusBar) (verticalPadding + 20).dp else verticalPadding.dp
    val botPad = if (showStatusBar) (verticalPadding + 28).dp else verticalPadding.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = horizontalPadding.dp,
                end = horizontalPadding.dp,
                top = topPad,
                bottom = botPad,
            ),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onTapZone(ReaderTapZone.fromX(offset.x, size.width.toFloat()))
                    }
                },
        ) {
            items(
                count = content.chapters.size,
                key = { content.chapters[it].index },
            ) { index ->
                val ch = content.chapters[index]
                val annotatedText = remember(ch.text, ch.startOffset, bookmarks, colors.highlightDefault) {
                    HighlightAnnotator.annotate(
                        text = ch.text,
                        startOffset = ch.startOffset,
                        endOffset = ch.startOffset + ch.text.length,
                        bookmarks = bookmarks,
                        defaultHighlightColor = colors.highlightDefault,
                    )
                }
                Text(
                    text = annotatedText,
                    style = textStyle,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }

        if (showStatusBar) {
            ReaderTopStatusBar(
                chapterTitle = chapterTitle,
                colors = colors,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
            val currentIdx = listState.firstVisibleItemIndex + 1
            val totalCh = content.chapters.size.coerceAtLeast(1)
            val pct = ((currentIdx.toFloat() / totalCh) * 100).toInt()
            ReaderBottomStatusBar(
                currentPage = currentIdx,
                totalPages = totalCh,
                progressPercent = pct,
                minutesRemaining = minutesRemaining,
                colors = colors,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }
    }
}
