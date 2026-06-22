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
import com.TopMob.bookmt.presentation.reader.ReaderTapZone
import com.TopMob.bookmt.presentation.theme.ReaderColors
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope



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
    listState: androidx.compose.foundation.lazy.LazyListState,
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

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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
            Text(
                text = content.chapters[index].text,
                style = textStyle,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}
