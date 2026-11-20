package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.BookmarkType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports a book's highlights, notes, and bookmarks into clean, structured Markdown format
 * suitable for note-taking tools (Obsidian, Notion, Logseq, etc.).
 */
class ExportAnnotationsUseCase {

    operator fun invoke(book: Book, annotations: List<Bookmark>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()

        sb.append("# ").append(book.title).append("\n\n")
        if (!book.author.isNullOrBlank()) {
            sb.append("**Автор:** ").append(book.author).append("\n\n")
        }
        sb.append("**Экспортировано:** ").append(dateFormat.format(Date())).append("\n\n")
        sb.append("---\n\n")

        if (annotations.isEmpty()) {
            sb.append("*Нет сохраненных заметок или цитат.*\n")
            return sb.toString()
        }

        val sorted = annotations.sortedBy { it.position }
        var currentChapter: String? = null

        for (item in sorted) {
            if (item.chapterTitle != null && item.chapterTitle != currentChapter) {
                currentChapter = item.chapterTitle
                sb.append("### ").append(currentChapter).append("\n\n")
            }

            when (item.type) {
                BookmarkType.HIGHLIGHT, BookmarkType.NOTE -> {
                    if (item.textSnippet.isNotBlank()) {
                        sb.append("> ").append(item.textSnippet.trim()).append("\n\n")
                    }
                    if (!item.note.isNullOrBlank()) {
                        sb.append("*Заметка:* ").append(item.note.trim()).append("\n\n")
                    }
                }
                BookmarkType.BOOKMARK -> {
                    sb.append("📌 **Закладка** (символ: ").append(item.position).append(")\n\n")
                    if (!item.note.isNullOrBlank()) {
                        sb.append("*Заметка:* ").append(item.note.trim()).append("\n\n")
                    }
                }
            }
        }

        return sb.toString()
    }
}
