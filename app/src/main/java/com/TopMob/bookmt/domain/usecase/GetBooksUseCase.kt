package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookQuery
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes the library and applies the active [BookQuery] (search, tag filter, sort). Filtering and
 * sorting are done in-memory here rather than in SQL so the bookshelf can react instantly to query
 * changes without re-hitting the DB, and so tag/progress logic stays in the domain layer.
 */
class GetBooksUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(query: BookQuery): Flow<List<Book>> =
        repository.observeBooks().map { books -> books.applyQuery(query) }

    private fun List<Book>.applyQuery(query: BookQuery): List<Book> {
        val filtered = filter { book ->
            val matchesSearch = query.searchQuery.isBlank() ||
                book.title.contains(query.searchQuery, ignoreCase = true) ||
                (book.author?.contains(query.searchQuery, ignoreCase = true) == true)

            val matchesTags = query.tags.isEmpty() || query.tags.all { it in book.tags }

            val matchesProgress = !query.onlyInProgress ||
                (book.progress > 0f && book.progress < 1f)

            matchesSearch && matchesTags && matchesProgress
        }

        val sorted = filtered.sortedWith(comparatorFor(query.sortOrder))
        return if (query.ascending) sorted else sorted.asReversed()
    }

    private fun comparatorFor(order: BookSortOrder): Comparator<Book> = when (order) {
        BookSortOrder.RECENTLY_ADDED -> compareBy { it.addedTimestamp }
        BookSortOrder.RECENTLY_READ -> compareBy { it.lastReadTimestamp }
        BookSortOrder.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        BookSortOrder.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.author ?: "" }
        BookSortOrder.PROGRESS -> compareBy { it.progress }
    }
}
