package com.TopMob.bookmt.data.local.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `tag serialization roundtrip`() {
        val tags = listOf("sci-fi", "favorite", "classic literature")
        val raw = converters.fromTagList(tags)
        val deserialized = converters.toTagList(raw)
        assertEquals(tags, deserialized)
    }

    @Test
    fun `empty and blank tags handled gracefully`() {
        assertEquals("", converters.fromTagList(emptyList()))
        assertEquals("", converters.fromTagList(listOf("", "  ")))
        assertEquals(emptyList<String>(), converters.toTagList(""))
        assertEquals(emptyList<String>(), converters.toTagList("   \n  \n"))
    }
}
