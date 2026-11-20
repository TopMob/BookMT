package com.TopMob.bookmt.presentation.reader

import com.TopMob.bookmt.domain.model.BookContent

/**
 * Slices a [BookContent] into discrete, bounded-size [Page]s at word boundaries.
 *
 * Slicing runs off the main thread in O(n) time. A single absolute character offset addresses
 * any location across chapters and pages.
 */
object ReaderPaginator {

    private const val DEFAULT_CHAR_BUDGET = 1500

    /**
     * Splits [content] into a list of [Page]s, each with length not exceeding [charBudget].
     */
    fun paginate(content: BookContent, charBudget: Int = DEFAULT_CHAR_BUDGET): List<Page> {
        val validBudget = charBudget.coerceAtLeast(100)
        val pages = mutableListOf<Page>()
        var globalPageIndex = 0

        for (chapter in content.chapters) {
            val chapterText = chapter.text
            if (chapterText.isEmpty()) continue

            var localStart = 0
            val textLength = chapterText.length

            while (localStart < textLength) {
                val remaining = textLength - localStart
                val sliceEnd = if (remaining <= validBudget) {
                    textLength
                } else {
                    val maxCandidate = localStart + validBudget
                    var breakPoint = -1
                    for (i in (maxCandidate - 1) downTo (localStart + 1)) {
                        if (chapterText[i].isWhitespace()) {
                            breakPoint = i + 1
                            break
                        }
                    }
                    if (breakPoint > localStart && breakPoint <= maxCandidate) {
                        breakPoint
                    } else {
                        maxCandidate
                    }
                }

                val sliceText = chapterText.substring(localStart, sliceEnd)
                val pageStart = chapter.startOffset + localStart
                val pageEnd = chapter.startOffset + sliceEnd

                pages += Page(
                    index = globalPageIndex++,
                    chapterIndex = chapter.index,
                    startOffset = pageStart,
                    endOffset = pageEnd,
                    text = sliceText,
                )

                localStart = sliceEnd
            }
        }

        return pages.ifEmpty {
            listOf(
                Page(
                    index = 0,
                    chapterIndex = 0,
                    startOffset = 0,
                    endOffset = 0,
                    text = "",
                )
            )
        }
    }

    /**
     * Returns the index of the [Page] in [pages] containing [offset].
     */
    fun pageIndexForOffset(pages: List<Page>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        if (offset <= pages.first().startOffset) return 0
        if (offset >= pages.last().endOffset) return pages.lastIndex

        var low = 0
        var high = pages.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val page = pages[mid]
            when {
                offset < page.startOffset -> high = mid - 1
                offset >= page.endOffset -> low = mid + 1
                else -> return mid
            }
        }
        return low.coerceIn(0, pages.lastIndex)
    }
}
