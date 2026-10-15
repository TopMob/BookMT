package com.TopMob.bookmt.data.backup

import android.content.Context
import android.net.Uri
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.local.BookMTDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.database.sqlite.SQLiteDatabase
import javax.inject.Inject

/**
 * Exports and restores the user's library (Room database) and preferences (DataStore files) as a
 * single ZIP, so statistics, bookmarks and settings survive a phone change.
 *
 * Restore overwrites the live data files and therefore requires an app restart — the caller is
 * expected to relaunch the process after a successful [import].
 */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BookMTDatabase,
    private val dispatchers: DispatcherProvider,
) {

    /** Writes a backup ZIP to [target]. */
    suspend fun export(target: Uri): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val snapshotFile = File.createTempFile("library-backup-", ".db", context.cacheDir)
            try {
                database.snapshot(snapshotFile)
                val output = context.contentResolver.openOutputStream(target)
                    ?: error("Cannot open output stream")
                ZipOutputStream(output.buffered()).use { zip ->
                    SQLiteDatabase.openDatabase(snapshotFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { snapshot ->
                        snapshot.rawQuery("SELECT id, uri, format, cover_image_path FROM books", null).use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(0)
                                val entry = "books/$id.${cursor.getString(2).lowercase().filter { it in 'a'..'z' }}"
                                val uri = Uri.parse(cursor.getString(1))
                                zip.putNextEntry(ZipEntry(entry))
                                context.contentResolver.openInputStream(uri)?.use { it.copyTo(zip) }
                                    ?: error("Cannot back up book #$id: source is unavailable")
                                zip.closeEntry()
                                snapshot.execSQL("UPDATE books SET uri=? WHERE id=?", arrayOf("bookmt-backup://$entry", id))
                                if (!cursor.isNull(3)) {
                                    val cover = File(cursor.getString(3))
                                    if (cover.isFile) {
                                        val coverEntry = "covers/$id.img"
                                        zip.putFile(coverEntry, cover)
                                        snapshot.execSQL("UPDATE books SET cover_image_path=? WHERE id=?", arrayOf("bookmt-backup://$coverEntry", id))
                                    }
                                }
                            }
                        }
                    }
                    zip.putFile(DB_ENTRY, snapshotFile)
                    dataStoreFiles().forEach { (entryName, file) -> zip.putFile(entryName, file) }
                    File(context.filesDir, "fonts").listFiles()?.filter { it.isFile && assetEntry("fonts/${it.name}") }
                        ?.forEach { zip.putFile("fonts/${it.name}", it) }
                }
            } finally { snapshotFile.delete() }
            Unit
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /** Restores from a backup ZIP at [source]. Caller must restart the app afterwards. */
    suspend fun import(source: Uri): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val staging = File(context.filesDir, "restore-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Cannot create restore staging directory" }
            val input = context.contentResolver.openInputStream(source)
                ?: error("Cannot open input stream")
            var restoredAnything = false
            val seen = mutableSetOf<String>()
            var totalBytes = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check((entry.name in allowedEntries || assetEntry(entry.name)) && !entry.isDirectory) { "Unknown backup entry: ${entry.name}" }
                    if ((entry.name in allowedEntries || assetEntry(entry.name)) && !entry.isDirectory) {
                        check(seen.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                        check(seen.size <= 100000) { "Too many backup entries" }
                        val targetFile = File(staging, entry.name)
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                check(totalBytes <= MAX_BACKUP_BYTES) { "Backup is too large" }
                                out.write(buffer, 0, count)
                            }
                        }
                        restoredAnything = true
                    }
                    zip.closeEntry()
                }
            }
            check(restoredAnything) { "Backup file contained no recognizable data" }
            val stagedDb = File(staging, DB_ENTRY)
            if (stagedDb.exists()) {
                SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    check(db.version in 1..3) { "Unsupported backup database version" }
                    db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getString(0) in setOf(
                            "aa0f6cfc7351c57ed761785a142e4a00", "e235dd67b0a52228c8e513074e295a72",
                            "7b7f1e18ec37febebc293fd51bdc8bca",
                        )) { "This is not a compatible BookMT backup" }
                    }
                    db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Corrupt backup database" }
                    }
                    db.rawQuery("SELECT id FROM books LIMIT 1", null).close()
                    db.rawQuery("SELECT id FROM bookmarks LIMIT 1", null).close()
                }
            }
            if (stagedDb.exists()) {
                SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.rawQuery("SELECT id, uri, cover_image_path FROM books", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            for ((column, index) in listOf("uri" to 1, "cover_image_path" to 2)) {
                                val value = if (cursor.isNull(index)) null else cursor.getString(index)
                                if (value?.startsWith("bookmt-backup://") == true) {
                                    val relative = value.removePrefix("bookmt-backup://")
                                    check(assetEntry(relative) && File(staging, relative).isFile) { "Missing book asset: $relative" }
                                    val destination = File(context.filesDir, relative)
                                    val replacement = if (column == "uri") Uri.fromFile(destination).toString() else destination.absolutePath
                                    db.execSQL("UPDATE books SET $column=? WHERE id=?", arrayOf(replacement, id))
                                }
                            }
                        }
                    }
                }
            }
            // Never replace files that Room or DataStore have open. Apply before their next startup.
            val pending = File(context.filesDir, PENDING_DIR)
            check(!pending.exists()) { "A restore is already pending; restart the app first" }
            check(staging.renameTo(pending)) { "Cannot stage backup for restart" }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    private fun dataStoreFiles(): List<Pair<String, File>> {
        val dir = File(context.filesDir, "datastore")
        return listOf(
            Constants.SETTINGS_DATASTORE_NAME,
            Constants.APP_SETTINGS_DATASTORE_NAME,
        ).map { name -> "$DATASTORE_DIR/$name.preferences_pb" to File(dir, "$name.preferences_pb") }
            .filter { it.second.exists() }
    }

    private fun ZipOutputStream.putFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    companion object {
        const val DB_ENTRY = "database/bookmt.db"
        const val DATASTORE_DIR = "datastore"
        private const val PENDING_DIR = "pending-restore"
        private const val MAX_BACKUP_BYTES = 4L * 1024 * 1024 * 1024
        private val allowedEntries = setOf(
            DB_ENTRY,
            "$DATASTORE_DIR/${Constants.SETTINGS_DATASTORE_NAME}.preferences_pb",
            "$DATASTORE_DIR/${Constants.APP_SETTINGS_DATASTORE_NAME}.preferences_pb",
        )

        private fun assetEntry(name: String): Boolean =
            Regex("books/[0-9]+\\.[a-z]+").matches(name) ||
                Regex("covers/[0-9]+\\.img").matches(name) ||
                Regex("fonts/[a-zA-Z0-9-]+\\.(ttf|otf)").matches(name)

        /** Runs before DI, Room and DataStore start. Staged sources remain until all writes succeed. */
        fun applyPendingRestore(context: Context) {
            val pending = File(context.filesDir, PENDING_DIR)
            if (!pending.isDirectory) return
            val assets = pending.walkTopDown().filter { it.isFile }
                .map { it.relativeTo(pending).invariantSeparatorsPath }.filter(::assetEntry).toList()
            // Install referenced assets before replacing the database that points to them.
            for (entry in assets + allowedEntries) {
                val source = File(pending, entry)
                if (!source.isFile) continue
                val target = if (entry == DB_ENTRY) context.getDatabasePath(Constants.DATABASE_NAME)
                    else File(context.filesDir, entry)
                target.parentFile?.mkdirs()
                val atomic = android.util.AtomicFile(target)
                val output = atomic.startWrite()
                try {
                    source.inputStream().use { it.copyTo(output) }
                    atomic.finishWrite(output)
                } catch (e: Exception) {
                    atomic.failWrite(output)
                    throw e
                }
                if (entry == DB_ENTRY) {
                    File(target.path + "-wal").delete()
                    File(target.path + "-shm").delete()
                }
            }
            pending.deleteRecursively()
        }
    }
}
