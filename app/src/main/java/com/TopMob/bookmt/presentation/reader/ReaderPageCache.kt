package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.text.TextStyle
import com.TopMob.bookmt.domain.model.BookContent
import java.lang.ref.WeakReference

/** One recent layout, bounded by text size. Never owns the source book or persists layout offsets. */
internal object ReaderPageCache {
    private data class Entry(val content: WeakReference<BookContent>, val style: TextStyle,
        val width: Int, val height: Int, val density: Float, val fontScale: Float, val pages: List<ReaderPage>)
    private var entry: Entry? = null

    @Synchronized
    fun get(content: BookContent, style: TextStyle, width: Int, height: Int, density: Float, fontScale: Float): List<ReaderPage>? {
        val cached = entry ?: return null
        return cached.pages.takeIf { cached.content.get() === content && cached.style == style &&
            cached.width == width && cached.height == height && cached.density == density && cached.fontScale == fontScale }
    }

    @Synchronized
    fun put(content: BookContent, style: TextStyle, width: Int, height: Int, density: Float, fontScale: Float, pages: List<ReaderPage>) {
        entry = if (content.totalLength <= 4_000_000)
            Entry(WeakReference(content), style, width, height, density, fontScale, pages) else null
    }
}
