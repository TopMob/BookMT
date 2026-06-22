package com.TopMob.bookmt.domain.model

/**
 * Supported book container formats. The set is intentionally open to extension — adding a new
 * format means adding an entry here plus a [com.TopMob.bookmt.domain.parser.BookParser]
 * implementation registered in the parser factory.
 */
enum class BookFormat(val extensions: Set<String>, val mimeTypes: Set<String>) {
    TXT(setOf("txt"), setOf("text/plain")),
    MARKDOWN(setOf("md", "markdown"), setOf("text/markdown", "text/x-markdown")),
    FB2(setOf("fb2"), setOf("application/x-fictionbook+xml")),
    PDF(setOf("pdf"), setOf("application/pdf")),
    EPUB(setOf("epub"), setOf("application/epub+zip")),
    UNKNOWN(emptySet(), emptySet());

    companion object {
        /** Resolves a format from a file name / path by its extension. */
        fun fromFileName(fileName: String): BookFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
        }

        fun fromMimeType(mimeType: String?): BookFormat {
            if (mimeType == null) return UNKNOWN
            return entries.firstOrNull { mimeType in it.mimeTypes } ?: UNKNOWN
        }
    }
}
