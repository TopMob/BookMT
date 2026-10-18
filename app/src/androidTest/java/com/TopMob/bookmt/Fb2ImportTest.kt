package com.TopMob.bookmt

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.core.common.DefaultDispatcherProvider
import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.data.local.BookMTDatabase
import com.TopMob.bookmt.data.mapper.*
import com.TopMob.bookmt.data.parser.*
import com.TopMob.bookmt.data.reader.DiskBookCache
import com.TopMob.bookmt.data.repository.BookRepositoryImpl
import com.TopMob.bookmt.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class Fb2ImportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = DefaultDispatcherProvider()
    private fun temp() = File(context.cacheDir, "fb2-import-${UUID.randomUUID()}").apply { mkdirs() }
    private fun image() = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(12, 18, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.BLUE); it.compress(Bitmap.CompressFormat.PNG, 100, out); it.recycle()
        }
    }.toByteArray()
    private fun header(cover: Boolean) = """<description><title-info><book-title>Полная книга &amp; текст</book-title>
        <author><first-name>Иван</first-name><middle-name>Петрович</middle-name><last-name>Иванов</last-name></author>
        <author><nickname>Второй автор</nickname></author><annotation><p>Первая строка аннотации.</p><p>Вторая <emphasis>строка</emphasis>.</p></annotation>
        ${if (cover) "<coverpage><image l:href='#cover'/></coverpage>" else ""}</title-info>
        <src-title-info><book-title>Original</book-title><author><nickname>Wrong author</nickname></author></src-title-info>
        <document-info><author><nickname>Scanner</nickname></author></document-info></description>""".trimIndent()
    private fun book(body: String, binary: String = "", cover: Boolean = false) =
        "<FictionBook xmlns='http://www.gribuser.ru/xml/fictionbook/2.0' xmlns:l='http://www.w3.org/1999/xlink'>${header(cover)}$body$binary</FictionBook>"

    @Test fun coverAtEndMetadataMultipleAuthorsAndAnnotationSurvivePrefixedXml() = runBlocking {
        val bytes = image()
        val encoded = Base64.encodeToString(bytes, Base64.DEFAULT)
        val xml = book("<body><section><p>Текст книги.</p></section></body>",
            "<binary id='other' content-type='image/png'>broken!</binary><binary id='cover' content-type='image/png'>$encoded</binary>", true)
        val prefixed = xml.replace("xmlns=", "xmlns:f=").replace(Regex("<(/?)([A-Za-z][A-Za-z0-9-]*)")) { "<${it.groupValues[1]}f:${it.groupValues[2]}" }
        val parser = Fb2BookParser(dispatchers)
        val metadata = parser.parseMetadata(prefixed.byteInputStream(), "test.fb2")
        assertEquals("Полная книга & текст", metadata.title)
        assertEquals("Иван Петрович Иванов, Второй автор", metadata.author)
        assertEquals("Первая строка аннотации.\nВторая строка.", metadata.description)
        assertArrayEquals(bytes, metadata.coverImageBytes)
    }

    @Test fun completeLargeCp1251BookKeepsEveryTextMarkerAcrossBodiesAndCacheRestart() = runBlocking {
        val root = temp()
        try {
            val expected = (0..11999).map { "TOK${it.toString().padStart(5, '0')}" }
            val parts = expected.chunked(4000).mapIndexed { index, words ->
                "<section id='s$index'><title><p>Глава $index</p></title>" + words.chunked(20).joinToString("") {
                    "<p>Русский <strong>${it.joinToString(" ")}</strong> &amp; текст.</p>"
                } + "<epigraph><p>Эпиграф $index.</p><text-author>Автор эпиграфа</text-author></epigraph>" +
                    "<poem><stanza><v>Стих первая строка.</v><v>Стих вторая строка.</v></stanza></poem></section>"
            }
            val xml = "<?xml version='1.0' encoding='windows-1251'?>" + book("<body>${parts[0]}<section>${parts[1]}</section></body><body name='notes'>${parts[2]}</body>")
            val source = File(root, "book.fb2").apply { writeBytes(xml.toByteArray(charset("windows-1251"))) }
            val parser = Fb2BookParser(dispatchers)
            val metadata = source.inputStream().use { parser.parseMetadata(it, "book.fb2") }
            assertEquals("Полная книга & текст", metadata.title)
            val cache = DiskBookCache(File(root, "cache")); val key = cache.key(source, BookFormat.FB2)
            val content = cache.writer(key).use { destination -> source.inputStream().use { parser.parseTo(it, "book.fb2", destination) } }
            val text = content.chapters.joinToString("") { it.text }
            assertEquals(expected, Regex("TOK[0-9]{5}").findAll(text).map { it.value }.toList())
            assertTrue(text.contains("Стих первая строка.\nСтих вторая строка."))
            assertTrue(content.chapters.any { it.auxiliary && it.text.contains("TOK11999") })
            assertTrue(content.chapters.all { it.text.length <= 24_000 })
            assertTrue(content.chapters.flatMap { it.spans }.any { it.style == ContentStyle.QUOTE })
            val restored = DiskBookCache(File(root, "cache")).read(key)!!
            assertEquals(text, restored.chapters.joinToString("") { it.text })
            assertEquals("Иван Петрович Иванов, Второй автор", restored.author)
        } finally { root.deleteRecursively() }
    }

    @Test fun brokenOptionalImageDoesNotDiscardTextOrFollowingValidImage() = runBlocking {
        val root = temp()
        try {
            val bytes = image()
            val xml = book("<body><section><p>Начало.</p><image l:href='#bad'/><p>Не потерять середину.</p><image l:href='#good'/><p>Конец.</p></section></body>",
                "<binary id='bad' content-type='image/png'>not-valid!</binary><binary id='good' content-type='image/png'>${Base64.encodeToString(bytes, Base64.NO_WRAP)}</binary>")
            val source = File(root, "book.fb2").apply { writeText(xml) }
            val cache = DiskBookCache(File(root, "cache"))
            val content = cache.writer(cache.key(source, BookFormat.FB2)).use { Fb2BookParser(dispatchers).parseTo(xml.byteInputStream(), "book.fb2", it) }
            assertTrue(content.chapters.joinToString("") { it.text }.contains("Не потерять середину."))
            assertTrue(content.chapters.last().text.contains("Конец."))
            assertFalse(content.resources.containsKey("bad"))
            assertArrayEquals(bytes, File(content.resources.getValue("good")).readBytes())
            val limited = Fb2BinaryData(3).apply { append(Base64.encodeToString(ByteArray(20), Base64.NO_WRAP)) }
            assertNull(limited.decode())
        } finally { root.deleteRecursively() }
    }

    @Test fun importedCoverAndAnnotationAreSavedAndLegacyMissingCoverRepairsWithoutReimport() = runBlocking {
        val root = temp()
        val db = Room.inMemoryDatabaseBuilder(context, BookMTDatabase::class.java).build()
        try {
            val bytes = image()
            val source = File(root, "source.fb2").apply { writeText(book("<body><section><p>Текст книги для сохранения позиций и отметок.</p></section></body>",
                "<binary id='cover' content-type='image/png'>${Base64.encodeToString(bytes, Base64.NO_WRAP)}</binary>", true)) }
            val books = File(root, "books").apply { mkdirs() }
            val repo = BookRepositoryImpl(db.bookDao(), BookParserFactory(setOf(Fb2BookParser(dispatchers))), context.contentResolver,
                dispatchers, storageDir = books, cacheDir = File(root, "cache"), bookmarkDao = db.bookmarkDao(), database = db)
            val imported = (repo.importBook(Uri.fromFile(source).toString(), "source.fb2", null) as Resource.Success).data
            assertArrayEquals(bytes, File(imported.coverImagePath!!).readBytes())
            assertTrue(imported.description!!.contains("Вторая строка."))
            db.bookDao().update(imported.copy(title = "Моё название", author = "Мой автор", description = "Мои заметки",
                coverImagePath = null, progress = .3f, lastReadPosition = 5, listeningPosition = 9,
                positionAnchor = "reading-anchor", listeningAnchor = "listening-anchor").toEntity())
            val markId = db.bookmarkDao().insert(Bookmark(bookId = imported.id, type = BookmarkType.BOOKMARK,
                position = 5, endPosition = 5, textSnippet = "Текст").toEntity())
            val before = db.bookDao().getById(imported.id)!!
            assertTrue(repo.loadContent(before.toDomain()) is Resource.Success)
            val after = db.bookDao().getById(imported.id)!!
            assertArrayEquals(bytes, File(after.coverImagePath!!).readBytes())
            assertEquals(before.copy(coverImagePath = after.coverImagePath), after)
            assertEquals(markId, db.bookmarkDao().getForBook(imported.id).single().id)
            val custom = File(root, "custom.png").apply { writeBytes(image()) }
            db.bookDao().update(after.copy(coverImagePath = custom.path))
            assertTrue(repo.loadContent(db.bookDao().getById(imported.id)!!.toDomain()) is Resource.Success)
            assertEquals(custom.path, db.bookDao().getById(imported.id)!!.coverImagePath)
        } finally { db.close(); root.deleteRecursively() }
    }
}
