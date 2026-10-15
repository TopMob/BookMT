package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StreamingReaderTest {
    @Test fun lateCp1251AndLongLinesDoNotRequireWholeBookBuffer() = runTest {
        val text = "a".repeat(100_000) + " Русский текст\r\nПоследняя строка"
        val parts = mutableListOf<StreamingTextSource.Fragment>()
        StreamingTextSource.copy(text.toByteArray(charset("windows-1251")).inputStream()).use { it.read(parts::add) }
        assertTrue(parts.all { it.text.length <= 24_001 })
        assertEquals(text.replace("\r\n", "\n") + "\n", parts.joinToString("") { it.text + if (it.endOfLine) "\n" else "" })
    }
    @Test fun htmlTokenizerHandlesEntitiesCommentsAndVoidTags() {
        val values = StringBuilder(); val tags = mutableListOf<HtmlBookEvents.Tag>()
        HtmlBookEvents.parse("<body><!-- x > y -->A&nbsp;<b>Б</b><br>С &#x1F600;</body>".reader(), {}, tags::add, values::append)
        assertEquals("A БС 😀", values.toString())
        assertTrue(tags.any { it.name == "br" && it.selfClosing })
        assertFalse(tags.any { it.name.startsWith("!") })
    }
    @Test fun blockFlushPreservesWordsWhitespaceAndOpenStyles() {
        val destination = MemoryBookDestination(); val builder = StructuredBookBuilder(destination)
        val text = "Слова и пробелы ".repeat(8000)
        builder.beginSource("source", "Глава"); builder.startStyle("strong", ContentStyle.BOLD)
        builder.append(text, collapseWhitespace = true); builder.endStyle("strong"); builder.flush()
        val result = destination.finish("Книга", null, TableOfContents.EMPTY)
        assertEquals(text, result.chapters.joinToString("") { it.text })
        assertTrue(result.chapters.all { it.spans.any { span -> span.style == ContentStyle.BOLD } })
    }
}
