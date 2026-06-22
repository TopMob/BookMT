package com.TopMob.bookmt.presentation.reader

/**
 * Horizontal tap regions on the paged reader: left third = previous page, right third = next page,
 * center third = toggle the reading controls (Moon+ Reader default tap behavior).
 */
enum class ReaderTapZone {
    LEFT, CENTER, RIGHT;

    companion object {
        private const val LEFT_BOUND = 0.33f
        private const val RIGHT_BOUND = 0.66f

        fun fromX(x: Float, width: Float): ReaderTapZone {
            if (width <= 0f) return CENTER
            return when (x / width) {
                in 0f..LEFT_BOUND -> LEFT
                in RIGHT_BOUND..1f -> RIGHT
                else -> CENTER
            }
        }
    }
}
