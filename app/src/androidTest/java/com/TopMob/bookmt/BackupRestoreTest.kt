package com.TopMob.bookmt

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.backup.BackupManager
import com.TopMob.bookmt.data.local.BookMTDatabase
import com.TopMob.bookmt.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupRestoreTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: BookMTDatabase
    private lateinit var manager: BackupManager
    private val entry = "datastore/${Constants.SETTINGS_DATASTORE_NAME}.preferences_pb"

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(app.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = root
            override fun getDatabasePath(name: String): File = File(root, "db/$name")
        }
        database = Room.inMemoryDatabaseBuilder(app, BookMTDatabase::class.java).build()
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val default = Dispatchers.Default
        }
        manager = BackupManager(context, database, dispatchers)
    }

    @After fun tearDown() { database.close(); root.deleteRecursively() }

    private fun backup(name: String, text: String): Uri {
        val file = File(root, "source.zip")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry(name)); it.write(text.toByteArray()); it.closeEntry()
        }
        return Uri.fromFile(file)
    }

    @Test fun restoreDoesNotTouchLivePreferencesUntilNextStartup() = runBlocking {
        val prefs = File(root, entry).apply { parentFile!!.mkdirs(); writeText("old") }
        assertTrue(manager.import(backup(entry, "restored")).isSuccess)
        assertEquals("old", prefs.readText())
        BackupManager.applyPendingRestore(context)
        assertEquals("restored", prefs.readText())
        assertFalse(File(root, "pending-restore").exists())
    }

    @Test fun rejectsTraversalWithoutReplacingExistingData() = runBlocking {
        val prefs = File(root, entry).apply { parentFile!!.mkdirs(); writeText("old") }
        assertTrue(manager.import(backup("datastore/../escaped", "bad")).isFailure)
        assertEquals("old", prefs.readText())
        assertFalse(File(root, "escaped").exists())
        assertFalse(File(root, "pending-restore").exists())
    }

    @Test fun settingsOnlyRestorePreservesOwnedBooksAndCovers() = runBlocking {
        val book = File(root, "books/existing.txt").apply { parentFile!!.mkdirs(); writeText("owned book") }
        val cover = File(root, "covers/existing.img").apply { parentFile!!.mkdirs(); writeText("owned cover") }
        assertTrue(manager.import(backup(entry, "restored preferences")).isSuccess)
        BackupManager.applyPendingRestore(context)
        assertEquals("owned book", book.readText())
        assertEquals("owned cover", cover.readText())
        assertFalse(File(root, "pending-restore").exists())
    }

    @Test fun unavailableBookMakesExportFailInsteadOfReportingPortableSuccess() = runBlocking {
        database.bookDao().insert(com.TopMob.bookmt.domain.model.Book(
            uri = Uri.fromFile(File(root, "missing.txt")).toString(), title = "Missing book",
            format = com.TopMob.bookmt.domain.model.BookFormat.TXT).toEntity())
        val existingBackup = File(root, "incomplete.zip").apply { writeText("previous backup") }
        assertTrue(manager.export(Uri.fromFile(existingBackup)).isFailure)
        assertEquals("previous backup", existingBackup.readText())
    }

    @Test fun failedStartupInstallRetainsBackupAndCanBeRetried() = runBlocking {
        val obstruction = File(root, "datastore").apply { writeText("keep this file") }
        val book = File(root, "books/existing.txt").apply { parentFile!!.mkdirs(); writeText("owned book") }
        assertTrue(manager.import(backup(entry, "restored preferences")).isSuccess)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { BackupManager.applyPendingRestore(context) }
        assertEquals("keep this file", obstruction.readText())
        assertTrue(File(root, "pending-restore").isDirectory)
        assertEquals("owned book", book.readText())
        assertTrue(obstruction.delete())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { BackupManager.applyPendingRestore(context) }
        assertEquals("restored preferences", File(root, entry).readText())
        assertFalse(File(root, "pending-restore").exists())
        assertEquals("owned book", book.readText())
    }

    @Test fun rejectsCorruptDatabaseBeforeStagingRestore() = runBlocking {
        assertTrue(manager.import(backup(BackupManager.DB_ENTRY, "not sqlite")).isFailure)
        assertFalse(File(root, "pending-restore").exists())
        assertFalse(context.getDatabasePath(Constants.DATABASE_NAME).exists())
    }

    @Test fun portableSchemaThreeBackupRetainsBothPositionsAndAnnotationAnchors() = runBlocking {
        val source = File(root, "original.txt").apply { writeText("Текст переносимой книги") }
        val anchor = com.TopMob.bookmt.domain.model.TextAnchor("chapter:0", 8, "Текст ", "книги").encode()
        val book = com.TopMob.bookmt.domain.model.Book(uri = Uri.fromFile(source).toString(), title = "Книга",
            format = com.TopMob.bookmt.domain.model.BookFormat.TXT, lastReadPosition = 8, positionAnchor = anchor,
            listeningPosition = 12, listeningAnchor = anchor, contentRevision = 2)
        val id = database.bookDao().insert(book.toEntity())
        database.bookmarkDao().insert(
            com.TopMob.bookmt.domain.model.Bookmark(bookId = id, type = com.TopMob.bookmt.domain.model.BookmarkType.NOTE,
                position = 8, endPosition = 12, note = "Заметка", startAnchor = anchor, endAnchor = anchor).toEntity()
        )
        val archive = Uri.fromFile(File(root, "portable.zip"))
        assertTrue(manager.export(archive).isSuccess)
        assertTrue(manager.import(archive).isSuccess)
        BackupManager.applyPendingRestore(context)
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(Constants.DATABASE_NAME).path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { restored ->
            assertEquals(3, restored.version)
            restored.rawQuery("SELECT uri,last_read_position,listening_position,position_anchor FROM books WHERE id=?", arrayOf(id.toString())).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(8, cursor.getInt(1)); assertEquals(12, cursor.getInt(2)); assertEquals(anchor, cursor.getString(3))
                assertEquals(source.readText(), File(Uri.parse(cursor.getString(0)).path!!).readText())
            }
            restored.rawQuery("SELECT note,start_anchor FROM bookmarks WHERE book_id=?", arrayOf(id.toString())).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("Заметка", cursor.getString(0)); assertEquals(anchor, cursor.getString(1))
            }
        }
    }
}
