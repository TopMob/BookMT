package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParseException
import com.TopMob.bookmt.domain.parser.BookParser
import java.io.InputStream

/** PDF content is rendered by the dedicated platform PdfRenderer reader. */
class PdfBookParser(
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.PDF

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.PDF ||
            BookFormat.fromMimeType(mimeType) == BookFormat.PDF

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        BookMetadata(title = fileName.substringBeforeLast('.').ifBlank { "PDF" })

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        throw BookParseException("PDF parsing is not yet implemented")
}
