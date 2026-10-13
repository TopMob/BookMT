package com.TopMob.bookmt.data.parser

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class ParserSupportTest {
    @Test fun `decodes UTF8 and strips BOM`() {
        assertEquals("Привет", "\uFEFFПривет".toByteArray(Charsets.UTF_8).decodeAsText())
    }
    @Test fun `decodes old Russian text books`() {
        assertEquals("Глава первая", "Глава первая".toByteArray(Charset.forName("windows-1251")).decodeAsText())
    }
    @Test fun `honors UTF16 BOM`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Книга".toByteArray(Charsets.UTF_16LE)
        assertEquals("Книга", bytes.decodeAsText())
    }
}
