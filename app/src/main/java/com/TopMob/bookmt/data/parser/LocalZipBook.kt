package com.TopMob.bookmt.data.parser

import java.io.*
import java.net.URLDecoder
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Random access to an EPUB without expanding the archive into a map of byte arrays. */
internal class LocalZipBook private constructor(private val file: File) : Closeable {
    val zip = ZipFile(file)
    init { require(zip.size() <= 20_000) { "EPUB has too many entries" } }
    private var readBytes = 0L
    fun open(path: String, maxBytes: Long = 32L * 1024 * 1024): InputStream? {
        val entry = zip.getEntry(path) ?: return null
        require(!entry.isDirectory && entry.size <= maxBytes) { "EPUB entry is too large" }
        return object : FilterInputStream(zip.getInputStream(entry)) {
            private var size = 0L
            private fun checkSize(count: Int) {
                if (count > 0) { size += count; readBytes += count
                    require(size <= maxBytes && readBytes <= 256L * 1024 * 1024) { "Expanded EPUB exceeds limit" }
                }
            }
            override fun read(): Int = super.read().also { if (it >= 0) checkSize(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also(::checkSize)
        }
    }
    override fun close() { zip.close(); file.delete() }
    companion object {
        suspend fun copy(input: InputStream, directory: File? = null): LocalZipBook {
            val file = File.createTempFile("bookmt-epub-", ".zip", directory)
            try {
                file.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 512L * 1024 * 1024) { "EPUB exceeds limit" }
                        out.write(buffer, 0, count)
                    }
                }
                return LocalZipBook(file)
            } catch (error: Throwable) { file.delete(); throw error }
        }
        fun resolve(base: String, href: String): String {
            val decoded = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
            require(!decoded.contains('\\') && !decoded.contains('\u0000')) { "Invalid EPUB resource path" }
            val parts = mutableListOf<String>()
            val full = if (decoded.startsWith('/')) decoded.drop(1) else if (base.isBlank()) decoded else "$base/$decoded"
            full.split('/').forEach { part -> when (part) {
                "", "." -> Unit
                ".." -> { require(parts.isNotEmpty()) { "EPUB resource escapes archive" }; parts.removeAt(parts.lastIndex) }
                else -> parts += part
            } }
            return parts.joinToString("/")
        }
    }
}
