package com.TopMob.bookmt.presentation.reader

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.domain.model.ContentStyle

data class RichPageText(val text: AnnotatedString, val placeholders: List<AnnotatedString.Range<Placeholder>>)

/** Identical spans and image geometry are used by measurement and rendering. */
fun ReaderPage.richText(style: TextStyle, widthPx: Int, heightPx: Int, density: Float, fontScale: Float,
    formatting: Boolean = true): RichPageText {
    val chapter = chapter()
    val images = chapter.images.associateBy { it.offset }
    val text = buildAnnotatedString {
        var cursor = 0
        images.toSortedMap().forEach { (offset, image) ->
            if (offset !in chapter.text.indices || chapter.text[offset] != '\uFFFC') return@forEach
            append(chapter.text.substring(cursor, offset)); appendInlineContent("image:$offset", "\uFFFC"); cursor = offset + 1
        }
        append(chapter.text.substring(cursor))
        if (formatting) chapter.spans.forEach { span ->
            val fontSize = style.fontSize.value.takeIf { it.isFinite() } ?: 18f
            val appearance = when (span.style) {
                ContentStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                ContentStyle.ITALIC, ContentStyle.QUOTE -> SpanStyle(fontStyle = FontStyle.Italic)
                ContentStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                ContentStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                ContentStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
                ContentStyle.HEADING -> SpanStyle(fontWeight = FontWeight.Bold,
                    fontSize = (fontSize * when (span.level) { 1 -> 1.4f; 2 -> 1.25f; else -> 1.12f }).sp)
                ContentStyle.SUBSCRIPT -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = (fontSize * .8f).sp)
                ContentStyle.SUPERSCRIPT -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = (fontSize * .8f).sp)
            }
            val start = span.start.coerceIn(0, length); val end = span.end.coerceIn(0, length)
            if (start < end) addStyle(appearance, start, end)
        }
        chapter.links.forEach { link ->
            val start = link.start.coerceIn(0, length); val end = link.end.coerceIn(0, length)
            if (start < end) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
    val placeholders = images.values.mapNotNull { image ->
        if (image.offset !in chapter.text.indices) return@mapNotNull null
        val ratio = if (image.width > 0 && image.height > 0) image.height.toFloat() / image.width else .6f
        val height = minOf(heightPx * .6f, widthPx * ratio).coerceAtLeast(1f)
        AnnotatedString.Range(Placeholder((widthPx / density / fontScale).sp,
            (height / density / fontScale).sp, PlaceholderVerticalAlign.Center), image.offset, image.offset + 1)
    }
    return RichPageText(text, placeholders)
}
