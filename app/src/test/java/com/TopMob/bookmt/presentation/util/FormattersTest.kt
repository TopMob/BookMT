package com.TopMob.bookmt.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatFileSize handles unknown or negative bytes`() {
        assertEquals("—", formatFileSize(0L))
        assertEquals("—", formatFileSize(-50L))
    }

    @Test
    fun `formatFileSize formats byte units properly`() {
        assertEquals("500 B", formatFileSize(500L))
        assertTrue(formatFileSize(1024L).contains("KB"))
        assertTrue(formatFileSize(1024L * 1024L * 2).contains("MB"))
    }

    @Test
    fun `formatDuration handles edge cases and spans`() {
        assertEquals("—", formatDuration(0L))
        assertEquals("—", formatDuration(-100L))
        assertEquals("<1m", formatDuration(30_000L))
        assertEquals("15m", formatDuration(15 * 60_000L))
        assertEquals("2h 15m", formatDuration((2 * 60 + 15) * 60_000L))
    }

    @Test
    fun `formatDate handles zero and valid epoch`() {
        assertEquals("—", formatDate(0L))
        assertEquals("—", formatDate(-1L))
        assertTrue(formatDate(1700000000000L).isNotBlank())
    }
}
