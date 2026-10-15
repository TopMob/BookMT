package com.TopMob.bookmt.data.parser

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Validates encoding with bounded buffers, including CP1251 late in otherwise ASCII files. */
internal class StreamingTextSource private constructor(private val file: File, private val charset: java.nio.charset.Charset, private val bom: Int) : Closeable {
    data class Fragment(val text: String, val endOfLine: Boolean, val startOfLine: Boolean)
    suspend fun read(onFragment: (Fragment) -> Unit) {
        file.inputStream().use { stream ->
            repeat(bom) { stream.read() }
            stream.bufferedReader(charset).use { reader ->
                val buffer = StringBuilder(); var start = true; var lastWasCr = false; var events = 0
                while (true) {
                    if (++events % 4096 == 0) currentCoroutineContext().ensureActive()
                    val char = reader.read(); if (char < 0) break
                    if (lastWasCr && char == '\n'.code) { lastWasCr = false; continue }
                    lastWasCr = char == '\r'.code
                    if (char == '\r'.code || char == '\n'.code) {
                        onFragment(Fragment(buffer.toString(), true, start)); buffer.clear(); start = true
                    } else {
                        buffer.append(char.toChar())
                        if (buffer.length >= 24_000 && !buffer.last().isHighSurrogate()) {
                            onFragment(Fragment(buffer.toString(), false, start)); buffer.clear(); start = false
                        }
                    }
                }
                // text.lines() preserves a final empty line; retain legacy TXT offsets.
                onFragment(Fragment(buffer.toString(), true, start))
            }
        }
    }
    override fun close() { file.delete() }
    companion object {
        suspend fun copy(input: InputStream): StreamingTextSource {
            val file = File.createTempFile("bookmt-text-", ".tmp")
            try {
                file.outputStream().buffered().use { out ->
                    val bytes = ByteArray(64 * 1024); var size = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(bytes); if (count < 0) break
                        size += count; require(size <= 512L * 1024 * 1024) { "Text book exceeds limit" }
                        out.write(bytes, 0, count)
                    }
                }
                val head = file.inputStream().use { stream -> ByteArray(3).also { stream.read(it) } }
                if (head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return StreamingTextSource(file, Charsets.UTF_16LE, 2)
                if (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return StreamingTextSource(file, Charsets.UTF_16BE, 2)
                val utf8 = validUtf8(file)
                val bom = if (utf8 && head.contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) 3 else 0
                return StreamingTextSource(file, if (utf8) Charsets.UTF_8 else java.nio.charset.Charset.forName("windows-1251"), bom)
            } catch (error: Throwable) { file.delete(); throw error }
        }
        private suspend fun validUtf8(file: File): Boolean {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val bytes = ByteBuffer.allocate(64 * 1024); val chars = CharBuffer.allocate(64 * 1024)
            file.inputStream().use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(bytes.array(), bytes.position(), bytes.remaining())
                    if (count >= 0) bytes.position(bytes.position() + count)
                    bytes.flip(); val result = decoder.decode(bytes, chars, count < 0)
                    if (result.isError) return false
                    bytes.compact(); chars.clear()
                    if (count < 0) return true
                }
            }
        }
    }
}
