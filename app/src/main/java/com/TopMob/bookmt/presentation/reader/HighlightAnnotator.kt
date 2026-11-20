package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import kotlin.math.max
import kotlin.math.min

/**
 * Applies visual highlight spans on page and chapter texts corresponding to stored user annotations.
 */
object HighlightAnnotator {

    /** Built-in pastel highlight palette (Moon+ / Apple Books style). */
    val Palette = listOf(
        0x66FFEB3B.toInt(), // Yellow
        0x6681C784.toInt(), // Mint Green
        0x6664B5F6.toInt(), // Sky Blue
        0x66F06292.toInt(), // Rose Pink
    )

    /**
     * Builds an [AnnotatedString] with background color spans applied for all highlights/notes
     * that intersect [[startOffset], [endOffset]).
     */
    fun annotate(
        text: String,
        startOffset: Int,
        endOffset: Int,
        bookmarks: List<Bookmark>,
        defaultHighlightColor: Color,
    ): AnnotatedString {
        if (text.isEmpty() || bookmarks.isEmpty()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            append(text)
            for (bm in bookmarks) {
                if (bm.type != BookmarkType.HIGHLIGHT && bm.type != BookmarkType.NOTE) continue
                val bmStart = bm.position
                val bmEnd = bm.endPosition.coerceAtLeast(bm.position + 1)

                val overlapStart = max(bmStart, startOffset)
                val overlapEnd = min(bmEnd, endOffset)

                if (overlapStart < overlapEnd) {
                    val localStart = (overlapStart - startOffset).coerceIn(0, text.length)
                    val localEnd = (overlapEnd - startOffset).coerceIn(localStart, text.length)
                    if (localStart < localEnd) {
                        val color = bm.colorArgb?.let { Color(it) } ?: defaultHighlightColor
                        addStyle(
                            style = SpanStyle(background = color),
                            start = localStart,
                            end = localEnd,
                        )
                    }
                }
            }
        }
    }
}
