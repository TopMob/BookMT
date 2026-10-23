package com.TopMob.bookmt.presentation.reader

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.domain.model.ContentStyle
import com.TopMob.bookmt.domain.model.ReaderFontFamily
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.model.ReaderTextAlignment
import java.io.File

/** Builds a Compose [TextStyle] from domain [ReaderSettings] + the active reader text [color]. */
fun ReaderSettings.toTextStyle(color: Color): TextStyle = TextStyle(
    color = color,
    fontSize = fontSizeSp.sp,
    // line-height as a multiple of font size keeps spacing proportional across font sizes.
    lineHeight = lineSpacingMultiplier.em,
    fontFamily = resolveFontFamily(),
    textAlign = textAlignment.toComposeTextAlign(),
)

/** Prefers a valid user-supplied font file, falling back to the built-in family if unusable. */
fun ReaderSettings.resolveFontFamily(): FontFamily {
    val path = customFontPath
    if (!path.isNullOrBlank()) {
        runCatching {
            val file = File(path)
            if (file.exists() && file.canRead()) return FontFamily(Font(file))
        }
    }
    return fontFamily.toComposeFontFamily()
}

fun ReaderFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReaderFontFamily.SERIF -> FontFamily.Serif
    ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
    ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
    ReaderFontFamily.SYSTEM -> FontFamily.Default
}

fun ReaderTextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    ReaderTextAlignment.START -> TextAlign.Start
    ReaderTextAlignment.JUSTIFY -> TextAlign.Justify
    ReaderTextAlignment.CENTER -> TextAlign.Center
}

data class RichPageText(val text: AnnotatedString, val placeholders: List<AnnotatedString.Range<Placeholder>>)

/** Identical spans and image geometry are used by measurement and rendering. */
fun ReaderPage.richText(
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    density: Float,
    fontScale: Float,
    formatting: Boolean = true,
): RichPageText {
    val chapter = chapter()
    val images = chapter.images.associateBy { it.offset }
    val text = buildAnnotatedString {
        var cursor = 0
        images.toSortedMap().forEach { (offset, _) ->
            if (offset !in chapter.text.indices || chapter.text[offset] != '\uFFFC') return@forEach
            append(chapter.text.substring(cursor, offset))
            appendInlineContent("image:$offset", "\uFFFC")
            cursor = offset + 1
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
                ContentStyle.HEADING -> SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize * when (span.level) { 1 -> 1.4f; 2 -> 1.25f; else -> 1.12f }).sp,
                )
                ContentStyle.SUBSCRIPT -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = (fontSize * .8f).sp)
                ContentStyle.SUPERSCRIPT -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = (fontSize * .8f).sp)
            }
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(0, length)
            if (start < end) addStyle(appearance, start, end)
        }
        chapter.links.forEach { link ->
            val start = link.start.coerceIn(0, length)
            val end = link.end.coerceIn(0, length)
            if (start < end) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
    val placeholders = images.values.mapNotNull { image ->
        if (image.offset !in chapter.text.indices) return@mapNotNull null
        val ratio = if (image.width > 0 && image.height > 0) image.height.toFloat() / image.width else .6f
        val height = minOf(heightPx * .6f, widthPx * ratio).coerceAtLeast(1f)
        AnnotatedString.Range(
            Placeholder((widthPx / density / fontScale).sp, (height / density / fontScale).sp, PlaceholderVerticalAlign.Center),
            image.offset,
            image.offset + 1,
        )
    }
    return RichPageText(text, placeholders)
}
