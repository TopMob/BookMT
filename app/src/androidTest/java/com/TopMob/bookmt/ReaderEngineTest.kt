package com.TopMob.bookmt

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderEngineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = DefaultDispatcherProvider()
    private fun temp() = File(context.cacheDir, "reader-test-${UUID.randomUUID()}").apply { mkdirs() }

    @Test fun migrationsFromBothOldVersionsKeepBooksAndAnnotations() = runBlocking {
        for (version in 1..2) {
            val name = "reader-migration-${UUID.randomUUID()}.db"
            val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
            try {
                val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                    .open("com.TopMob.bookmt.data.local.BookMTDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
                SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.insertOrThrow("books", null, ContentValues().apply {
                        put("id", 42); put("uri", "file:///legacy.fb2"); put("title", "Старая книга"); put("format", "FB2")
                        put("progress_percent", 1f); put("last_read_position", 77); put("last_read_timestamp", 100L); put("added_timestamp", 50L); put("tags", "")
                        if (version >= 2) { put("file_size", 200L); put("total_reading_time_ms", 9000L) }
                    })
                    db.insertOrThrow("bookmarks", null, ContentValues().apply {
                        put("id", 99); put("book_id", 42); put("type", "HIGHLIGHT"); put("position", 77); put("end_position", 88)
                        put("text_snippet", "Не потерять"); put("note", "Старая заметка"); put("timestamp", 100L)
                    })
                    db.version = version
                }
                val db = Room.databaseBuilder(context, BookMTDatabase::class.java, name)
                    .addMigrations(BookMTDatabase.MIGRATION_1_2, BookMTDatabase.MIGRATION_2_3).build()
                try {
                    val book = db.bookDao().getById(42)!!
                    assertEquals(77, book.lastReadPosition); assertTrue(book.completed); assertEquals(1, book.contentRevision)
                    val mark = db.bookmarkDao().getForBook(42).single()
                    assertEquals(99L, mark.id); assertEquals("Старая заметка", mark.note); assertEquals(88, mark.endPosition)
                    db.bookDao().updateProgress(42, .2f, 20, 200)
                    assertTrue(db.bookDao().getById(42)!!.completed)
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun fb2PreservesNestedHeadingsStylesFootnotesAndInlineImage() = runBlocking {
        val root = temp()
        try {
            val bytes = image()
            val xml = """<FictionBook xmlns:l="http://www.w3.org/1999/xlink"><description><title-info><book-title>Книга</book-title><author><first-name>Иван</first-name><last-name>Иванов</last-name></author></title-info></description><body><section id="main"><title><p>Главная глава</p></title><p>Обычный <strong>важный</strong> и <emphasis>курсивный</emphasis> текст. <a l:href="#n1">Сноска</a></p><image l:href="#picture"/><section id="nested"><title><p>Вложенная глава</p></title><p>Продолжение.</p></section></section></body><body name="notes"><section id="n1"><title><p>Примечание</p></title><p>Текст примечания.</p></section></body><binary id="picture" content-type="image/png">${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}</binary></FictionBook>"""
            val source = File(root, "book.fb2").apply { writeText(xml) }
            val cache = DiskBookCache(File(root, "cache"))
            val content = cache.writer(cache.key(source, BookFormat.FB2)).use { Fb2BookParser(dispatchers).parseTo(xml.byteInputStream(), "book.fb2", it) }
            assertEquals("Иван Иванов", content.author)
            assertEquals(listOf("Главная глава", "Вложенная глава", "Примечание"), content.tableOfContents.entries.map { it.title })
            assertEquals(listOf(0, 1, 0), content.tableOfContents.entries.map { it.level })
            assertEquals("Главная глава", content.chapters.first().title)
            assertTrue(content.chapters.flatMap { it.spans }.any { it.style == ContentStyle.BOLD })
            assertTrue(content.chapters.flatMap { it.links }.any { it.target == "#n1" })
            assertTrue(TextAnchors.range(content, content.targets.getValue("#n1"), content.totalLength).contains("Текст примечания"))
            assertArrayEquals(bytes, File(content.resources.getValue("picture")).readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun epubNavigationAndNonlinearFootnotesWorkWithoutInflatingAllEntries() = runBlocking {
        val root = temp()
        try {
            val file = epub(root)
            val cache = DiskBookCache(File(root, "cache"))
            val parser = EpubBookParser(dispatchers, root)
            val content = cache.writer(cache.key(file, BookFormat.EPUB)).use { destination -> file.inputStream().use { parser.parseTo(it, "book.epub", destination) } }
            assertEquals("Глава из навигации", content.tableOfContents.entries.first().title)
            assertTrue(content.targets.containsKey("EPUB/notes.xhtml#n1"))
            assertTrue(content.chapters.any { it.auxiliary && "Текст примечания" in it.text })
            assertTrue(content.chapters.first().text.contains("Добропожаловать"))
            assertTrue(content.chapters.first().spans.any { it.style == ContentStyle.BOLD })
            assertTrue(File(content.resources.getValue("EPUB/picture.png")).isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun legacyAnnotationRelocationAndFreshProcessCacheKeepIdentity() = runBlocking {
        val root = temp(); val db = Room.inMemoryDatabaseBuilder(context, BookMTDatabase::class.java).build()
        try {
            val books = File(root, "books").apply { mkdirs() }; val source = epub(books)
            val parser = EpubBookParser(dispatchers, root)
            val old = source.inputStream().use { parser.parse(it, "book.epub") }
            val full = old.chapters.joinToString("") { it.text }
            val start = full.indexOf("Уникальная цитата")
            assertTrue(start > 0)
            val id = db.bookDao().insert(Book(uri = Uri.fromFile(source).toString(), title = "Book", format = BookFormat.EPUB,
                lastReadPosition = start, contentRevision = 1).toEntity())
            val markId = db.bookmarkDao().insert(Bookmark(bookId = id, type = BookmarkType.HIGHLIGHT, position = start,
                endPosition = start + "Уникальная цитата".length, textSnippet = "Уникальная цитата", note = "Сохранить", contentRevision = 1).toEntity())
            fun repository() = BookRepositoryImpl(db.bookDao(), BookParserFactory(setOf(parser)), context.contentResolver,
                dispatchers, storageDir = books, cacheDir = File(root, "cache"), bookmarkDao = db.bookmarkDao(), database = db)
            val content = (repository().loadContent(db.bookDao().getById(id)!!.toDomain()) as Resource.Success).data
            val saved = db.bookDao().getById(id)!!
            val mark = db.bookmarkDao().getForBook(id).single()
            assertEquals(markId, mark.id); assertEquals("Сохранить", mark.note); assertEquals(2, saved.contentRevision)
            assertEquals("Уникальная цитата", TextAnchors.range(content, mark.position, mark.endPosition))
            assertEquals(mark.position, saved.lastReadPosition)
            val again = (repository().loadContent(saved.toDomain()) as Resource.Success).data
            assertEquals(content.cacheKey, again.cacheKey)
            assertEquals(content.totalLength, again.totalLength)
        } finally { db.close(); root.deleteRecursively() }
    }

    private fun image(): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.RED); it.compress(Bitmap.CompressFormat.PNG, 100, out); it.recycle() }
    }.toByteArray()

    private fun epub(root: File): File {
        val entries = linkedMapOf(
            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='EPUB/package.opf'/></rootfiles></container>",
            "EPUB/package.opf" to """<package><metadata><title>Книга EPUB</title><creator>Автор</creator></metadata><manifest><item id="main" href="main.xhtml" media-type="application/xhtml+xml"/><item id="notes" href="notes.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="main"/><itemref idref="notes" linear="no"/></spine></package>""",
            "EPUB/main.xhtml" to "<html><head><title>Title</title></head><body><h1 id='chapter'>Заголовок</h1><p>Добро<strong>пожаловать</strong> в книгу. Уникальная цитата для проверки сохранённых отметок и позиции чтения. Здесь достаточно текста после цитаты для сопоставления.</p><p><a href='notes.xhtml#n1'>Сноска</a></p><img src='picture.png' alt='Картинка'></body></html>",
            "EPUB/notes.xhtml" to "<html><body><section id='n1'><h2>Примечание</h2><p>Текст примечания.</p></section></body></html>",
            "EPUB/nav.xhtml" to "<html><body><nav epub:type='toc'><ol><li><a href='main.xhtml#chapter'>Глава из навигации</a></li></ol></nav></body></html>",
        )
        return File(root, "book.epub").apply { ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            zip.putNextEntry(ZipEntry("EPUB/picture.png")); zip.write(image()); zip.closeEntry()
        } }
    }
}
