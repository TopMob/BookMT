package com.TopMob.bookmt.domain.parser

import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import java.io.InputStream

/**
 * Extensible contract for turning a raw book file into structured [BookContent].
 *
 * Each supported format provides one implementation. New formats are added by implementing this
 * interface and registering it in [BookParserFactory] — no other layer needs to change.
 *
 * Implementations must be **stream-based and non-blocking-friendly**: callers invoke them off the
 * main thread, and parsers should read incrementally rather than slurping the whole file where the
 * format allows, to keep memory bounded for large books.
 */
interface BookParser {

    /** The format this parser handles. */
    val format: BookFormat

    /** Cheap check (typically by extension / mime / magic bytes) used by the factory for dispatch. */
    fun canParse(fileName: String, mimeType: String?): Boolean

    /**
     * Reads lightweight metadata (title, author) without fully parsing the body — used when
     * importing a book into the library so the bookshelf populates instantly.
     */
    suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata

    /**
     * Fully parses the book into chapters + table of contents. Called lazily when a book is opened.
     *
     * @throws BookParseException if the content is malformed or unsupported.
     */
    suspend fun parse(input: InputStream, fileName: String): BookContent
}

/** Minimal metadata extracted during import. */
data class BookMetadata(
    val title: String,
    val author: String? = null,
    val coverImageBytes: ByteArray? = null,
) {
    // ByteArray needs structural equality overrides to behave correctly in data-class comparisons.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookMetadata) return false
        return title == other.title &&
            author == other.author &&
            coverImageBytes.contentEqualsNullable(other.coverImageBytes)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (coverImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}

class BookParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
