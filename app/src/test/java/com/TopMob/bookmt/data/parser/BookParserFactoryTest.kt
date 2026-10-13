package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookParser
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class BookParserFactoryTest {
    @Test fun `file extension takes precedence over misleading provider MIME`() {
        val txt = mockk<BookParser>()
        val epub = mockk<BookParser>()
        every { txt.format } returns BookFormat.TXT
        every { epub.format } returns BookFormat.EPUB
        val factory = BookParserFactory(linkedSetOf(txt, epub))
        assertSame(epub, factory.parserFor("BOOK.EPUB", "text/plain"))
    }
}
