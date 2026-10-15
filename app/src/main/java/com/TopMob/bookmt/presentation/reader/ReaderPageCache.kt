package com.TopMob.bookmt.presentation.reader

import androidx.compose.ui.text.TextStyle
import com.TopMob.bookmt.domain.model.BookContent
import java.lang.ref.WeakReference

/** One recent layout, bounded by text size. Never owns the source book or persists layout offsets. */
internal object ReaderPageCache {
    private data class Entry(val content: WeakReference<BookContent>, val style: TextStyle,
        val width: Int, val height: Int, val density: Float, val fontScale: Float, val formatting: Boolean, val pages: WeakReference<List<ReaderPage>>)
    private var entry: Entry? = null

    @Synchronized
    fun get(content: BookContent, style: TextStyle, width: Int, height: Int, density: Float, fontScale: Float, formatting: Boolean = true): List<ReaderPage>? {
        val cached = entry
        if (cached != null && cached.content.get() === content && cached.style == style && cached.width == width &&
            cached.height == height && cached.density == density && cached.fontScale == fontScale && cached.formatting == formatting) cached.pages.get()?.let { return it }
        val file = layoutFile(content, style, width, height, density, fontScale, formatting) ?: return null
        return runCatching { java.io.DataInputStream(file.inputStream().buffered()).use { input ->
            check(input.readInt() == 0x424D5042)
            val count = input.readInt(); check(count in 1..100_000)
            val pages = List(count) {
                val chapter = input.readInt(); val start = input.readInt(); val end = input.readInt()
                val d = checkNotNull(content.descriptor(chapter)); check(start >= 0 && end > start && end <= d.length)
                ReaderPage(content, chapter, start, end)
            }
            check(pages.first().startOffset == 0 && pages.last().endOffset == content.totalLength)
            check(pages.zipWithNext().all { (a, b) -> a.endOffset == b.startOffset })
            pages
        } }.getOrNull()
    }

    @Synchronized
    fun put(content: BookContent, style: TextStyle, width: Int, height: Int, density: Float, fontScale: Float, pages: List<ReaderPage>, formatting: Boolean = true) {
        entry = Entry(WeakReference(content), style, width, height, density, fontScale, formatting, WeakReference(pages))
        val file = layoutFile(content, style, width, height, density, fontScale, formatting) ?: return
        val temporary = java.io.File(file.parentFile, file.name + ".tmp")
        runCatching {
            java.io.DataOutputStream(temporary.outputStream().buffered()).use { out ->
                out.writeInt(0x424D5042); out.writeInt(pages.size)
                pages.forEach { out.writeInt(it.chapterIndex); out.writeInt(it.localOffset); out.writeInt(it.localOffset + it.length) }
            }
            check(temporary.renameTo(file))
            file.parentFile?.listFiles()?.filter { it.name.startsWith("layout-") && it.extension == "bin" }
                ?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
        }.onFailure { temporary.delete() }
    }

    private fun layoutFile(content: BookContent, style: TextStyle, width: Int, height: Int, density: Float, fontScale: Float, formatting: Boolean): java.io.File? {
        val directory = content.cacheDirectory ?: return null
        val value = "2|${com.TopMob.bookmt.BuildConfig.VERSION_CODE}|${android.os.Build.FINGERPRINT}|${java.util.Locale.getDefault()}|${content.cacheKey}|$style|$width|$height|$density|$fontScale|$formatting"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        return java.io.File(directory, "layout-$hash.bin")
    }
}
