package com.TopMob.bookmt.data.reader

import androidx.room.withTransaction
import com.TopMob.bookmt.data.local.BookMTDatabase
import com.TopMob.bookmt.data.local.entity.BookmarkEntity
import com.TopMob.bookmt.domain.model.*

/** Derives old anchors before changing offsets; commits every annotation and position together. */
internal class PositionMigration(private val database: BookMTDatabase) {
    suspend fun relocate(book: Book, marks: List<BookmarkEntity>, legacy: BookContent?, content: BookContent): BookContent {
        var warnings = 0
        fun anchor(stored: String?, offset: Int) = stored ?: legacy?.let { TextAnchors.capture(it, offset)?.encode() }
        val readingAnchor = anchor(book.positionAnchor, book.lastReadPosition)
        val reading = if (book.lastReadPosition == 0 && book.positionAnchor == null) TextAnchors.Resolution(0, true)
            else TextAnchors.resolve(content, readingAnchor, book.lastReadPosition)
        if (book.lastReadPosition > 0 && !reading.matched) warnings++
        val listening = TextAnchors.resolve(content, book.listeningAnchor, book.listeningPosition)
        val updated = marks.map { mark ->
            val startAnchor = anchor(mark.startAnchor, mark.position)
            val endAnchor = anchor(mark.endAnchor, mark.endPosition)
            val start = TextAnchors.resolve(content, startAnchor, mark.position)
            val end = TextAnchors.resolve(content, endAnchor, mark.endPosition)
            val matched = start.matched && end.matched && end.offset >= start.offset
            if (!matched) warnings++
            val preservedStart = mark.position.coerceIn(0, content.totalLength)
            val preservedEnd = mark.endPosition.coerceIn(preservedStart, content.totalLength)
            mark.copy(position = if (matched) start.offset else preservedStart,
                endPosition = if (matched) end.offset else preservedEnd,
                startAnchor = if (matched) TextAnchors.capture(content, start.offset)?.encode() else startAnchor,
                endAnchor = if (matched) TextAnchors.capture(content, end.offset)?.encode() else endAnchor,
                contentRevision = if (matched) content.revision else mark.contentRevision)
        }
        database.withTransaction {
            // Reload fields that may have changed during parsing; never overwrite user metadata.
            val current = database.bookDao().getById(book.id) ?: return@withTransaction
            database.bookDao().update(current.copy(lastReadPosition = reading.offset,
                positionAnchor = if (reading.matched) TextAnchors.capture(content, reading.offset)?.encode() else readingAnchor,
                listeningPosition = listening.offset, listeningAnchor = if (listening.matched) TextAnchors.capture(content, listening.offset)?.encode() else book.listeningAnchor,
                contentRevision = if (book.lastReadPosition == 0 || reading.matched) content.revision else current.contentRevision))
            updated.forEach { database.bookmarkDao().updateAnchors(it.id, it.position, it.endPosition, it.startAnchor, it.endAnchor, it.contentRevision) }
        }
        val offsets = linkedMapOf(book.lastReadPosition to reading.offset)
        marks.zip(updated).forEach { (old, new) -> offsets[old.position] = new.position; offsets[old.endPosition] = new.endPosition }
        return content.copy(positionWarnings = warnings, relocatedOffsets = offsets)
    }
}
