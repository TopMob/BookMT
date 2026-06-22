package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
