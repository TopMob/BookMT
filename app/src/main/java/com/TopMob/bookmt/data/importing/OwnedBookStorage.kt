package com.TopMob.bookmt.data.importing

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

object OwnedBookStorage {
    /** Content-addressed copies remain available after the original document is moved or removed. */
    suspend fun copy(resolver: ContentResolver, source: Uri, directory: File, extension: String): Uri {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create book storage" }
        val temporary = File.createTempFile("import-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(source)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 512L * 1024 * 1024) { "Book exceeds 512 MB" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "Book file is empty" }
                }
            } ?: error("Cannot open book")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "$hash.$extension")
            if (!target.exists()) check(temporary.renameTo(target)) { "Cannot store book" }
            return Uri.fromFile(target)
        } finally { temporary.delete() }
    }
}
