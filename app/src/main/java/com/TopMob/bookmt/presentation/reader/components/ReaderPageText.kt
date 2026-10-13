package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.presentation.reader.BookSearchResult
import com.TopMob.bookmt.presentation.reader.ReaderPage

@Composable
fun ReaderPageText(page: ReaderPage, style: TextStyle, bookmarks: List<Bookmark>, matches: List<BookSearchResult>,
    onSelection: (ReaderSelection) -> Unit, onTap: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    var layout by remember(page) { mutableStateOf<TextLayoutResult?>(null) }
    val latestSelection by rememberUpdatedState(onSelection)
    val latestTap by rememberUpdatedState(onTap)
    val text = remember(page, bookmarks, matches) {
        buildAnnotatedString {
            append(page.text)
            fun mark(start: Int, end: Int, color: Color) {
                val from = (start - page.startOffset).coerceIn(0, length)
                val to = (end - page.startOffset).coerceIn(0, length)
                if (from < to) addStyle(SpanStyle(background = color.copy(alpha = 0.45f)), from, to)
            }
            bookmarks.forEach { mark(it.position, it.endPosition, Color(it.colorArgb ?: 0xFFFFD54F.toInt())) }
            matches.forEach { mark(it.offset, it.offset + it.length, Color(0xFF80CBC4)) }
        }
    }
    Text(text, style = style, onTextLayout = { layout = it }, modifier = modifier.pointerInput(page) {
        detectTapGestures(onTap = { latestTap(it.x, size.width.toFloat()) }, onLongPress = { point ->
            layout?.let {
                if (page.text.isNotEmpty()) {
                    val offset = it.getOffsetForPosition(point).coerceIn(0, page.text.lastIndex)
                    latestSelection(ReaderSelection(page, it.getWordBoundary(offset)))
                }
            }
        })
    })
}
