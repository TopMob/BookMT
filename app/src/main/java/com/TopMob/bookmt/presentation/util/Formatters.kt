package com.TopMob.bookmt.presentation.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Human-readable file size (e.g. "1.4 MB"). Returns "—" for unknown (0) sizes. */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

/** Compact duration like "2h 15m" / "15m" / "<1m" from a millisecond span. */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalMinutes = ms / 60_000L
    if (totalMinutes < 1L) return "<1m"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Locale-aware medium date (e.g. "Jun 22, 2026"). Returns "—" for epoch 0. */
fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
}
