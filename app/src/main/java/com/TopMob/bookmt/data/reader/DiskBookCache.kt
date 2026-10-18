package com.TopMob.bookmt.data.reader

import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.parser.BookContentDestination
import java.io.*
import java.security.MessageDigest
import java.util.UUID

/** Versioned, disposable content cache. Originals and user data never live here. */
class DiskBookCache(private val root: File) {
    fun key(source: File, format: BookFormat): String = hash("$CACHE_VERSION|$REVISION|${source.canonicalPath}|${source.length()}|${source.lastModified()}|$format")
    fun read(key: String): BookContent? = runCatching { readDocument(File(root, key), key) }.getOrNull()
    fun pin(content: BookContent) { synchronized(live) { live += java.lang.ref.WeakReference(content) } }
    fun writer(key: String): Writer {
        check(root.isDirectory || root.mkdirs()) { "Cannot create reading cache" }
        return Writer(File(root, ".$key-${UUID.randomUUID()}.tmp"), File(root, key), key)
    }
    fun prune(activeKey: String, maxBytes: Long = 512L * 1024 * 1024) {
        val pinned = synchronized(live) {
            live.removeAll { it.get() == null }
            live.mapNotNull { it.get()?.cacheKey }.toSet() + activeKey
        }
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith('.') &&
            System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.deleteRecursively() }
        val entries = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.sortedBy { it.lastModified() }.orEmpty()
        fun size(entry: File) = File(entry, "size.bytes").takeIf { it.isFile }?.readText()?.toLongOrNull()
            ?: entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        var total = entries.sumOf(::size)
        for (entry in entries) {
            if (total <= maxBytes) break
            if (entry.name !in pinned) {
                val bytes = size(entry)
                if (entry.deleteRecursively()) total -= bytes
            }
        }
    }

    inner class Writer(private val staging: File, private val destination: File, private val key: String) : BookContentDestination, Closeable {
        private val descriptors = mutableListOf<ChapterDescriptor>()
        private val assets = linkedMapOf<String, String>()
        private val targets = linkedMapOf<String, Int>()
        private var committed = false
        private var bytesWritten = 0L
        override var length = 0; private set
        init { check(staging.mkdirs()) { "Cannot create reading cache transaction" } }

        override fun append(chapter: Chapter) {
            var cursor = 0
            do {
                var end = minOf(cursor + BLOCK_SIZE, chapter.text.length)
                if (end < chapter.text.length) {
                    val boundary = chapter.text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                    if (boundary >= maxOf(cursor + 1, end - 512)) end = boundary + 1
                }
                if (end < chapter.text.length && end > cursor && chapter.text[end - 1].isHighSurrogate()) end--
                val index = descriptors.size
                val part = Chapter(index, chapter.title, chapter.text.substring(cursor, end), length,
                    chapter.sourceId, chapter.sourceOffset + cursor,
                    chapter.spans.mapNotNull { span ->
                        val start = maxOf(span.start, cursor); val stop = minOf(span.end, end)
                        if (start < stop) span.copy(start = start - cursor, end = stop - cursor) else null
                    }, chapter.links.mapNotNull { link ->
                        val start = maxOf(link.start, cursor); val stop = minOf(link.end, end)
                        if (start < stop) link.copy(start = start - cursor, end = stop - cursor) else null
                    }, chapter.images.filter { it.offset in cursor until end }.map { it.copy(offset = it.offset - cursor) }, chapter.auxiliary)
                val blockFile = File(staging, "$index.block")
                val checksum = java.util.zip.CRC32()
                DataOutputStream(java.util.zip.CheckedOutputStream(blockFile.outputStream().buffered(), checksum)).use { writeChapter(it, part) }
                DataOutputStream(java.io.FileOutputStream(blockFile, true)).use { it.writeLong(checksum.value) }
                bytesWritten += blockFile.length()
                descriptors += part.descriptor()
                length = Math.addExact(length, part.text.length)
                cursor = end
            } while (cursor < chapter.text.length)
        }

        override fun resource(id: String, input: InputStream, maxBytes: Long) {
            val fileName = "asset-${hash(id)}"
            val file = File(staging, fileName)
            file.outputStream().buffered().use { out ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= maxBytes) { "Book resource exceeds limit" }
                    out.write(buffer, 0, count)
                }
            }
            assets[id] = fileName
            bytesWritten += file.length()
        }
        override fun target(id: String, offset: Int) { if (targets.size < 100_000) targets.putIfAbsent(id, offset) }
        override fun targetOffset(id: String) = targets[id]

        override fun finish(title: String, author: String?, toc: TableOfContents): BookContent {
            check(!committed)
            DataOutputStream(File(staging, "index.bin").outputStream().buffered()).use { out ->
                out.writeInt(MAGIC); out.writeInt(CACHE_VERSION); out.string(title); out.string(author.orEmpty())
                out.writeInt(descriptors.size)
                descriptors.forEach { d ->
                    out.writeInt(d.index); out.string(d.title); out.writeInt(d.startOffset); out.writeInt(d.length)
                    out.string(d.sourceId); out.writeInt(d.sourceOffset); out.writeBoolean(d.auxiliary)
                }
                out.writeInt(toc.entries.size)
                toc.entries.forEach { out.string(it.title); out.writeInt(it.offset); out.writeInt(it.level) }
                out.map(assets); out.writeInt(targets.size)
                targets.forEach { (id, offset) -> out.string(id); out.writeInt(offset) }
            }
            File(staging, "size.bytes").writeText((bytesWritten + File(staging, "index.bin").length()).toString())
            if (destination.exists()) check(destination.deleteRecursively()) { "Cannot replace stale cache" }
            check(staging.renameTo(destination)) { "Cannot commit reading cache" }
            committed = true
            return readDocument(destination, key)
        }
        override fun close() { if (!committed) staging.deleteRecursively() }
    }

    companion object {
        private val live = mutableListOf<java.lang.ref.WeakReference<BookContent>>()
        const val REVISION = 2
        private const val CACHE_VERSION = 3
        const val BLOCK_SIZE = 24_000
        private const val MAGIC = 0x424D5443
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun readDocument(directory: File, key: String): BookContent {
            DataInputStream(File(directory, "index.bin").inputStream().buffered()).use { input ->
                check(input.readInt() == MAGIC && input.readInt() == CACHE_VERSION) { "Stale content cache" }
                val title = input.string(); val author = input.string().ifBlank { null }
                val descriptors = List(input.count()) {
                    ChapterDescriptor(input.readInt(), input.string(), input.readInt(), input.readInt(), input.string(), input.readInt(), input.readBoolean())
                }
                var expected = 0
                descriptors.forEachIndexed { index, d ->
                    check(d.index == index && d.startOffset == expected && d.length in 0..BLOCK_SIZE && File(directory, "$index.block").isFile)
                    expected = Math.addExact(expected, d.length)
                }
                val toc = TableOfContents(List(input.count()) { TocEntry(input.string(), input.readInt(), input.readInt()) })
                val assets = buildMap {
                    repeat(input.count()) {
                        val id = input.string(); val file = input.string()
                        check(file.matches(Regex("asset-[a-f0-9]{64}")) && File(directory, file).isFile)
                        put(id, File(directory, file).absolutePath)
                    }
                }
                val targets = buildMap { repeat(input.count()) { put(input.string(), input.readInt()) } }
                directory.setLastModified(System.currentTimeMillis())
                val content = BookContent(title, author, StoredChapters(directory, descriptors), toc, assets, targets,
                    key, directory.absolutePath, REVISION)
                synchronized(live) { live += java.lang.ref.WeakReference(content) }
                return content
            }
        }

        private class StoredChapters(private val directory: File, private val descriptors: List<ChapterDescriptor>) : AbstractList<Chapter>(), ChapterCollection {
            private val recent = object : LinkedHashMap<Int, Chapter>(8, .75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Chapter>?) = size > 6
            }
            override val size get() = descriptors.size
            override fun descriptor(index: Int) = descriptors[index]
            @Synchronized override fun get(index: Int): Chapter = recent.getOrPut(index) {
                val d = descriptors[index]
                val checksum = java.util.zip.CRC32()
                val loaded = DataInputStream(java.util.zip.CheckedInputStream(File(directory, "$index.block").inputStream().buffered(), checksum)).use { input ->
                    val chapter = readChapter(input, d)
                    val actual = checksum.value
                    check(input.readLong() == actual && input.read() == -1) { "Damaged reading cache" }
                    chapter
                }
                check(loaded.text.length == d.length) { "Damaged reading cache" }
                loaded
            }
        }

        private fun writeChapter(out: DataOutputStream, chapter: Chapter) {
            out.string(chapter.text); out.writeInt(chapter.spans.size)
            chapter.spans.forEach { out.writeInt(it.start); out.writeInt(it.end); out.writeInt(it.style.ordinal); out.writeInt(it.level) }
            out.writeInt(chapter.links.size); chapter.links.forEach { out.writeInt(it.start); out.writeInt(it.end); out.string(it.target) }
            out.writeInt(chapter.images.size); chapter.images.forEach { out.writeInt(it.offset); out.string(it.resourceId); out.string(it.alt); out.writeInt(it.width); out.writeInt(it.height) }
        }
        private fun readChapter(input: DataInputStream, d: ChapterDescriptor): Chapter {
            val text = input.string()
            val spans = List(input.count()) { ContentSpan(input.readInt(), input.readInt(), ContentStyle.entries[input.readInt()], input.readInt()) }
            val links = List(input.count()) { ContentLink(input.readInt(), input.readInt(), input.string()) }
            val images = List(input.count()) { ContentImage(input.readInt(), input.string(), input.string(), input.readInt(), input.readInt()) }
            return Chapter(d.index, d.title, text, d.startOffset, d.sourceId, d.sourceOffset, spans, links, images, d.auxiliary)
        }
        private fun DataOutputStream.string(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); writeInt(bytes.size); write(bytes) }
        private fun DataInputStream.string(): String { val size = readInt(); require(size in 0..2_000_000); return ByteArray(size).also(::readFully).toString(Charsets.UTF_8) }
        private fun DataInputStream.count(): Int = readInt().also { require(it in 0..100_000) }
        private fun DataOutputStream.map(values: Map<String, String>) { writeInt(values.size); values.forEach { (k, v) -> string(k); string(v) } }
    }
}
