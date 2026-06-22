package com.TopMob.bookmt.data.parser

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.parser.BookMetadata
import com.TopMob.bookmt.domain.parser.BookParseException
import com.TopMob.bookmt.domain.parser.BookParser
import java.io.InputStream

/**
 * Structural placeholder for PDF support.
 *
 * PDF requires a dedicated text-extraction engine (e.g. PdfBox-Android or a native renderer for
 * image-based pages). This class establishes the seam so the rest of the app can already enumerate
 * PDF as a supported [BookFormat]; [parse] throws until a real extractor is wired in.
 *
 * Implementation plan:
 *  1. Add a PDF text-extraction dependency.
 *  2. Extract per-page text into [RawChapter]s (one per page, or merge by detected headings).
 *  3. Build a [com.TopMob.bookmt.domain.model.TableOfContents] from the PDF outline/bookmarks.
 */
class PdfBookParser(
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : BookParser {

    override val format: BookFormat = BookFormat.PDF

    override fun canParse(fileName: String, mimeType: String?): Boolean =
        BookFormat.fromFileName(fileName) == BookFormat.PDF ||
            BookFormat.fromMimeType(mimeType) == BookFormat.PDF

    override suspend fun parseMetadata(input: InputStream, fileName: String): BookMetadata =
        BookMetadata(title = fileName.substringBeforeLast('.').ifBlank { fileName })

    override suspend fun parse(input: InputStream, fileName: String): BookContent =
        throw BookParseException("PDF parsing is not yet implemented")
}
