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
            // Fold the WAL back into the main db file so the copy is self-contained.
            checkpoint()
            val output = context.contentResolver.openOutputStream(target)
                ?: error("Cannot open output stream")
            ZipOutputStream(output.buffered()).use { zip ->
                dbFile()?.let { zip.putFile(DB_ENTRY, it) }
                dataStoreFiles().forEach { (entryName, file) -> zip.putFile(entryName, file) }
            }
        }
    }

    /** Restores from a backup ZIP at [source]. Caller must restart the app afterwards. */
    suspend fun import(source: Uri): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            checkpoint()
            val input = context.contentResolver.openInputStream(source)
                ?: error("Cannot open input stream")
            var restoredAnything = false
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val targetFile = fileForEntry(entry.name)
                    if (targetFile != null) {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out -> zip.copyTo(out) }
                        restoredAnything = true
                    }
                    zip.closeEntry()
                }
            }
            check(restoredAnything) { "Backup file contained no recognizable data" }
            // Stale WAL/SHM would otherwise be replayed over the freshly restored db.
            deleteWalShm()
        }
    }

    private fun checkpoint() = runCatching {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }

    private fun dbFile(): File? = context.getDatabasePath(Constants.DATABASE_NAME).takeIf { it.exists() }

    private fun dataStoreFiles(): List<Pair<String, File>> {
        val dir = File(context.filesDir, "datastore")
        return listOf(
            Constants.SETTINGS_DATASTORE_NAME,
            Constants.APP_SETTINGS_DATASTORE_NAME,
        ).map { name -> "$DATASTORE_DIR/$name.preferences_pb" to File(dir, "$name.preferences_pb") }
            .filter { it.second.exists() }
    }

    /** Maps a ZIP entry name back to its on-device destination, or null if unknown. */
    private fun fileForEntry(name: String): File? = when {
        name == DB_ENTRY -> context.getDatabasePath(Constants.DATABASE_NAME)
        name.startsWith("$DATASTORE_DIR/") ->
            File(File(context.filesDir, "datastore"), name.removePrefix("$DATASTORE_DIR/"))
        else -> null
    }

    private fun deleteWalShm() {
        val db = context.getDatabasePath(Constants.DATABASE_NAME)
        File(db.path + "-wal").delete()
        File(db.path + "-shm").delete()
    }

    private fun ZipOutputStream.putFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private companion object {
        const val DB_ENTRY = "database/bookmt.db"
        const val DATASTORE_DIR = "datastore"
    }
}
