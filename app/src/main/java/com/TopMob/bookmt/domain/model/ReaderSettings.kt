package com.TopMob.bookmt.domain.model

/**
 * User-tunable reading preferences, persisted via DataStore. Pure domain type consumed by the
 * Reader UI; immutable so it flows cleanly through Compose state.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.DAY,
    val fontSizeSp: Float = 18f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val lineSpacingMultiplier: Float = 1.4f,
    val horizontalMarginDp: Int = 24,
    val verticalMarginDp: Int = 16,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    /** When true, an edge swipe controls system brightness instead of the global setting. */
    val brightnessOverrideEnabled: Boolean = false,
    /** Manual brightness 0f..1f used when [brightnessOverrideEnabled] is true. */
    val brightnessLevel: Float = 0.5f,
    val keepScreenOn: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 10f
        const val MAX_FONT_SIZE_SP = 40f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.5f
    }
}

/** Built-in and custom reading themes. Concrete colors live in the presentation theme layer. */
enum class ReaderTheme { DAY, SEPIA, NIGHT, AMOLED_BLACK, CUSTOM }

enum class ReaderFontFamily { SERIF, SANS_SERIF, MONOSPACE, SYSTEM }

enum class ReaderTextAlignment { START, JUSTIFY, CENTER }

enum class ReadingMode {
    /** Discrete pages flipped via tap zones / swipes. */
    PAGED,

    /** Continuous vertical scrolling. */
    SCROLL,
}
