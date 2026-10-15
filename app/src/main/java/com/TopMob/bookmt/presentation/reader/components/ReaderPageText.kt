package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.presentation.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun ReaderPageText(page: ReaderPage, style: TextStyle, bookmarks: List<Bookmark>, matches: List<BookSearchResult>,
    onSelection: (ReaderSelection) -> Unit, onTap: (Float, Float) -> Unit, modifier: Modifier = Modifier,
    formatting: Boolean = true, viewportHeightPx: Int = 1000, onLink: (String) -> Unit = {}, onImage: (String, String) -> Unit = { _, _ -> },
    onLoadError: (Throwable) -> Unit = {}, spokenSegment: com.TopMob.bookmt.domain.model.TextSegment? = null) {
    val latestError by rememberUpdatedState(onLoadError)
    val loaded by produceState<ReaderPage?>(null, page) {
        try { value = withContext(Dispatchers.IO) { page.materialized() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { latestError(e) }
    }
    val snapshot = loaded ?: return
    val chapter = remember(snapshot) { snapshot.chapter() }
    var layout by remember(snapshot) { mutableStateOf<TextLayoutResult?>(null) }
    val latestSelection by rememberUpdatedState(onSelection)
    val latestTap by rememberUpdatedState(onTap)
    val latestLink by rememberUpdatedState(onLink)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val rich = remember(snapshot, style, width, viewportHeightPx, density.density, density.fontScale, formatting) {
            snapshot.richText(style, width, viewportHeightPx, density.density, density.fontScale, formatting)
        }
        val text = remember(rich, bookmarks, matches, spokenSegment) {
            buildAnnotatedString {
                append(rich.text)
                fun mark(start: Int, end: Int, color: Color) {
                    val from = (start - page.startOffset).coerceIn(0, length)
                    val to = (end - page.startOffset).coerceIn(0, length)
                    if (from < to) addStyle(SpanStyle(background = color.copy(alpha = 0.45f)), from, to)
                }
                bookmarks.forEach { mark(it.position, it.endPosition, Color(it.colorArgb ?: 0xFFFFD54F.toInt())) }
                matches.forEach { mark(it.offset, it.offset + it.length, Color(0xFF80CBC4)) }
                spokenSegment?.let { mark(it.startOffset, it.endOffset, Color(0xFF64B5F6)) }
            }
        }
        val inline = remember(snapshot, rich) { chapter.images.mapNotNull { image ->
            val placeholder = rich.placeholders.firstOrNull { it.start == image.offset }?.item ?: return@mapNotNull null
            val path = snapshot.resource(image.resourceId)
            "image:${image.offset}" to InlineTextContent(placeholder) {
                if (path != null) AsyncImage(File(path), image.alt.ifBlank { "Иллюстрация" }, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { onImage(path, image.alt) })
                else Text(image.alt.ifBlank { "Иллюстрация недоступна" })
            }
        }.toMap() }
        Text(text, style = style, inlineContent = inline, onTextLayout = { layout = it }, modifier = Modifier.fillMaxSize().pointerInput(snapshot) {
            detectTapGestures(onTap = { point ->
                val offset = layout?.getOffsetForPosition(point)
                val link = offset?.let { value -> chapter.links.firstOrNull { value in it.start until it.end } }
                if (link != null) latestLink(link.target) else latestTap(point.x, size.width.toFloat())
            }, onLongPress = { point ->
                layout?.let {
                    if (chapter.text.isNotEmpty()) {
                        val offset = it.getOffsetForPosition(point).coerceIn(0, chapter.text.lastIndex)
                        latestSelection(ReaderSelection(snapshot, it.getWordBoundary(offset)))
                    }
                }
            })
        })
    }
}
