package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import com.TopMob.bookmt.domain.model.ReadingMode
import com.TopMob.bookmt.presentation.reader.ReaderPage
import kotlinx.coroutines.flow.distinctUntilChanged

/** Pagination completion is observable even when the visible position does not change. */
@Composable
internal fun ReportReaderPages(
    pages: List<ReaderPage>, complete: Boolean, mode: ReadingMode,
    pager: PagerState, list: LazyListState, restored: Boolean,
    onPageChanged: (Int, Int) -> Unit,
) {
    val latestCallback by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pages, complete, mode, pager, list, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow {
            val index = if (mode == ReadingMode.PAGED) pager.settledPage else list.firstVisibleItemIndex
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()
            val bottom = mode == ReadingMode.SCROLL && last != null && last.index == pages.lastIndex &&
                last.offset + last.size <= list.layoutInfo.viewportEndOffset
            (if (bottom) pages.size else index + 1).coerceIn(1, pages.size) to
                if (complete) pages.size else -pages.size
        }.distinctUntilChanged().collect { (page, total) -> latestCallback(page, total) }
    }
}
