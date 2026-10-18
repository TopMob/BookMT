package com.TopMob.bookmt.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/** Startup-only file transaction. Originals remain on disk until the whole restore commits. */
internal class RestoreTransaction(
    private val directory: File,
    private val targetFor: (String) -> File,
) {
    private val manifest get() = File(directory, "manifest")
    private val committed get() = File(directory, "committed")

    /** Returns true if a previous process finished installing every file. */
    fun recover(): Boolean {
        if (!directory.exists()) return false
        if (committed.isFile) return true
        rollback()
        finish()
        return false
    }

    fun install(replacements: Map<String, File?>, afterInstall: (Int) -> Unit = {}) {
        check(!directory.exists()) { "An earlier restore has not been recovered" }
        check(directory.mkdirs())
        try {
            // Prepare every replacement before moving any live file, including the database WAL.
            replacements.entries.forEachIndexed { index, (_, source) ->
                if (source != null) durableCopy(source, File(directory, "$index.new"))
            }
            val entries = replacements.keys.map { name ->
                val target = targetFor(name)
                require(!target.exists() || target.isFile) { "Restore target is not a file: $name" }
                name to target.exists()
            }
            val temporaryManifest = File(directory, "manifest.tmp")
            FileOutputStream(temporaryManifest).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(entries.size)
                entries.forEach { (name, existed) -> out.writeUTF(name); out.writeBoolean(existed) }
                out.flush()
                stream.fd.sync()
            }
            check(temporaryManifest.renameTo(manifest))
            entries.forEachIndexed { index, (name, existed) ->
                val target = targetFor(name)
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                if (existed) check(target.renameTo(File(directory, "$index.old"))) { "Cannot preserve $name" }
                val replacement = File(directory, "$index.new")
                if (replacement.exists()) check(replacement.renameTo(target)) { "Cannot install $name" }
                afterInstall(index)
            }
            val commitFile = File(directory, "commit.tmp")
            FileOutputStream(commitFile).use { it.write(1); it.fd.sync() }
            check(commitFile.renameTo(committed))
        } catch (error: Exception) {
            // If rollback fails, retain its journal and stop startup rather than open mixed data.
            rollback()
            finish()
            throw error
        }
    }

    fun finish() {
        if (!directory.exists()) return
        // Keep the commit marker until cleanup has removed every rollback source. An interrupted
        // cleanup must never make a committed transaction look like an unfinished installation.
        val files = checkNotNull(directory.listFiles()) { "Cannot list restore journal" }
        files.filter { it != committed }.forEach {
            check(it.deleteRecursively()) { "Cannot remove restore journal file" }
        }
        check(!committed.exists() || committed.delete())
        check(directory.delete()) { "Cannot remove restore journal" }
    }

    private fun rollback() {
        if (!manifest.isFile) return // Preparation failed before any live file was touched.
        val entries = DataInputStream(manifest.inputStream().buffered()).use { input ->
            val count = input.readInt()
            require(count in 0..100_010)
            List(count) { input.readUTF() to input.readBoolean() }
        }
        entries.withIndex().reversed().forEach { (index, entry) ->
            val (name, existed) = entry
            val target = targetFor(name)
            val original = File(directory, "$index.old")
            if (original.exists()) {
                check(!target.exists() || target.delete()) { "Cannot roll back $name" }
                check(original.renameTo(target)) { "Cannot restore $name" }
            } else if (!existed) {
                check(!target.exists() || target.delete()) { "Cannot roll back $name" }
            }
        }
    }

    private fun durableCopy(source: File, target: File) {
        FileOutputStream(target).use { output ->
            source.inputStream().use { it.copyTo(output) }
            output.fd.sync()
        }
    }
}
