package com.TopMob.bookmt.data.backup

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file(path: String, text: String) = File(temporary.root, path).apply {
        parentFile!!.mkdirs(); writeText(text)
    }
    private fun transaction() = RestoreTransaction(File(temporary.root, "journal")) { File(temporary.root, "live/$it") }

    @Test fun failedInstallRestoresDatabaseWalPreferencesAndNewAssets() {
        val db = file("live/database", "old database")
        val wal = file("live/database-wal", "committed positions")
        val prefs = file("live/preferences", "old preferences")
        val source = file("staging/database", "new database")
        val asset = file("staging/book", "new book")
        val replacements = linkedMapOf("book" to asset, "database" to source,
            "database-wal" to null, "preferences" to source)
        assertThrows(IOException::class.java) {
            transaction().install(replacements) { if (it == 3) throw IOException("disk full") }
        }
        assertEquals("old database", db.readText())
        assertEquals("committed positions", wal.readText())
        assertEquals("old preferences", prefs.readText())
        assertFalse(File(temporary.root, "live/book").exists())
        assertEquals("new database", source.readText())
        assertFalse(File(temporary.root, "journal").exists())
    }

    @Test fun nextStartupRollsBackProcessInterruptedBetweenFiles() {
        val db = file("live/database", "old database")
        val wal = file("live/database-wal", "old WAL")
        val source = file("staging/database", "new database")
        assertThrows(SimulatedProcessDeath::class.java) {
            transaction().install(linkedMapOf("database" to source, "database-wal" to null)) {
                if (it == 1) throw SimulatedProcessDeath()
            }
        }
        assertEquals("new database", db.readText())
        assertFalse(wal.exists())
        assertFalse(transaction().recover())
        assertEquals("old database", db.readText())
        assertEquals("old WAL", wal.readText())
        assertTrue(source.isFile)
        assertFalse(transaction().recover())
    }

    @Test fun committedInstallSurvivesProcessRestartUntilJournalCleanup() {
        val db = file("live/database", "old")
        val source = file("staging/database", "restored")
        transaction().install(linkedMapOf("database" to source))
        assertTrue(transaction().recover())
        assertEquals("restored", db.readText())
        transaction().finish()
        assertFalse(transaction().recover())
        assertEquals("restored", db.readText())
    }

    @Test fun preparationFailureDoesNotTouchAnyLiveFile() {
        val db = file("live/database", "old")
        val source = file("staging/database", "restored")
        assertThrows(IOException::class.java) {
            transaction().install(linkedMapOf("database" to source,
                "preferences" to File(temporary.root, "missing")))
        }
        assertEquals("old", db.readText())
        assertFalse(File(temporary.root, "journal").exists())
    }

    private class SimulatedProcessDeath : Error()
}
