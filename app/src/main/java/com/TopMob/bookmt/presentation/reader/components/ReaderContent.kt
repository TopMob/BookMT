package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.presentation.reader.ReaderPage
import com.TopMob.bookmt.presentation.reader.ReaderTapZone
import com.TopMob.bookmt.presentation.theme.ReaderColors
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Paged reading surface backed by [HorizontalPager]. Only the visible (and adjacent pre-loaded)
 * pages are composed, so memory and layout cost stay constant regardless of book size. Tap zones on
 * the left/center/right thirds drive page turns and control toggling.
 */
@Composable
fun PagedReaderContent(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    textStyle: TextStyle,
    colors: ReaderColors,
    horizontalPadding: Int,
    verticalPadding: Int,
    onPageSettled: (Int) -> Unit,
    onTapZone: (ReaderTapZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size },
    )

    // External jumps (seek bar, TOC) -> move the pager.
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex != pagerState.currentPage) {
            pagerState.scrollToPage(currentPageIndex)
        }
    }

    // Pager settles on a page -> report back to the VM (debounced via settle, deduped here).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onPageSettled)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTapZone(ReaderTapZone.fromX(offset.x, size.width.toFloat()))
                }
            },
    ) { pageIndex ->
        val page = pages[pageIndex]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        ) {
            Text(text = page.text, style = textStyle)
        }
    }
}

/**
 * Continuous vertical-scroll reading surface. Each chapter is one lazy item, so very long books are
 * never fully laid out at once. The top-most visible chapter's offset is reported for progress.
 */
@Composable
fun ScrollReaderContent(
    content: BookContent,
    textStyle: TextStyle,
    colors: ReaderColors,
    horizontalPadding: Int,
    verticalPadding: Int,
    onOffsetChanged: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                content.chapters.getOrNull(index)?.let { onOffsetChanged(it.startOffset) }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        items(
            count = content.chapters.size,
            key = { content.chapters[it].index },
        ) { index ->
            Text(
                text = content.chapters[index].text,
                style = textStyle,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}
