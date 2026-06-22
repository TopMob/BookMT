package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookParser

/**
 * Resolves the right [BookParser] for a given file. Parsers are injected as a set (see ParserModule)
 * so adding a new format is purely additive — bind a new parser and the factory picks it up.
 */
class BookParserFactory(private val parsers: Set<@JvmSuppressWildcards BookParser>) {

    /** Finds a parser that accepts the file, or null if the format is unsupported. */
    fun parserFor(fileName: String, mimeType: String?): BookParser? =
        parsers.firstOrNull { it.canParse(fileName, mimeType) }

    fun parserFor(format: BookFormat): BookParser? =
        parsers.firstOrNull { it.format == format }

    val supportedFormats: Set<BookFormat> get() = parsers.map { it.format }.toSet()
}
