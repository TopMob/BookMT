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
import androidx.compose.ui.res.stringResource
import com.TopMob.bookmt.R
import com.TopMob.bookmt.presentation.theme.annotationColor
import java.io.File

@Composable
fun ReaderPageText(page: ReaderPage, style: TextStyle, bookmarks: List<Bookmark>, matches: List<BookSearchResult>,
    onSelection: (ReaderSelection) -> Unit, onTap: (Float, Float) -> Unit, modifier: Modifier = Modifier,
    formatting: Boolean = true, viewportHeightPx: Int = 1000, onLink: (String) -> Unit = {}, onImage: (String, String) -> Unit = { _, _ -> },
    onLoadError: (Throwable) -> Unit = {}, spokenSegment: com.TopMob.bookmt.domain.model.TextSegment? = null,
    ttsHighlightColor: Color = Color(0x6642A5F5),
    onPageLayout: (TextLayoutResult?) -> Unit = {}) {
    val palette = com.TopMob.bookmt.presentation.theme.LocalComfortPalette.current
    val latestError by rememberUpdatedState(onLoadError)
    val loaded by produceState<ReaderPage?>(null, page) {
        try { value = withContext(Dispatchers.IO) { page.materialized() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { latestError(e) }
    }
    val latestPageLayout by rememberUpdatedState(onPageLayout)
    DisposableEffect(page) {
        val releaseLayout = onPageLayout
        onDispose { releaseLayout(null) }
    }
    val snapshot = loaded
    if (snapshot == null) {
        // A zero-height lazy item lets initial scrollToItem() consume the requested
        // offset before its text arrives, and can compose thousands of empty pages.
        Box(modifier.height(with(LocalDensity.current) { viewportHeightPx.toDp() }))
        return
    }
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
        val pageStart = page.startOffset
        val pageEnd = page.endOffset
        val visibleBookmarks = remember(bookmarks, pageStart, pageEnd) {
            bookmarks.filter { it.position < pageEnd && it.endPosition > pageStart }
        }
        val visibleMatches = remember(matches, pageStart, pageEnd) {
            matches.filter { it.offset < pageEnd && (it.offset + it.length) > pageStart }
        }
        val text = remember(rich, visibleBookmarks, visibleMatches, spokenSegment, ttsHighlightColor, palette) {
            buildAnnotatedString {
                append(rich.text)
                fun mark(start: Int, end: Int, color: Color) {
                    val from = (start - page.startOffset).coerceIn(0, length)
                    val to = (end - page.startOffset).coerceIn(0, length)
                    if (from < to) addStyle(SpanStyle(background = color), from, to)
                }
                visibleBookmarks.forEach { mark(it.position, it.endPosition, palette.annotationColor(it.colorArgb)) }
                visibleMatches.forEach { mark(it.offset, it.offset + it.length, palette.search) }
                spokenSegment?.let { segment ->
                    val from = (segment.startOffset - page.startOffset).coerceIn(0, length)
                    val to = (segment.endOffset - page.startOffset).coerceIn(0, length)
                    if (from < to) {
                        addStyle(
                            SpanStyle(
                                background = ttsHighlightColor,
                            ),
                            from,
                            to,
                        )
                    }
                }
            }
        }
        val defaultAlt = stringResource(R.string.reader_image)
        val unavailAlt = stringResource(R.string.reader_image_unavailable)
        val inline = remember(snapshot, rich, defaultAlt, unavailAlt) { chapter.images.mapNotNull { image ->
            val placeholder = rich.placeholders.firstOrNull { it.start == image.offset }?.item ?: return@mapNotNull null
            val path = snapshot.resource(image.resourceId)
            "image:${image.offset}" to InlineTextContent(placeholder) {
                if (path != null) AsyncImage(File(path), image.alt.ifBlank { defaultAlt }, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { onImage(path, image.alt) })
                else Text(image.alt.ifBlank { unavailAlt })
            }
        }.toMap() }
        Text(text, style = style, inlineContent = inline, onTextLayout = { layout = it; latestPageLayout(it) }, modifier = Modifier.fillMaxSize().pointerInput(snapshot) {
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
