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

    @Test fun rejectsCorruptDatabaseBeforeStagingRestore() = runBlocking {
        assertTrue(manager.import(backup(BackupManager.DB_ENTRY, "not sqlite")).isFailure)
        assertFalse(File(root, "pending-restore").exists())
        assertFalse(context.getDatabasePath(Constants.DATABASE_NAME).exists())
    }
}
