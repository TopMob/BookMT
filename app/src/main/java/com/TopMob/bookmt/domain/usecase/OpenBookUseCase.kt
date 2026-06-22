package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.BookContent
import com.TopMob.bookmt.domain.repository.BookRepository
import javax.inject.Inject

/** Loads a book and its fully-parsed content for the reader, refreshing its last-read timestamp. */
class OpenBookUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(bookId: Long): Resource<BookContent> {
        val book = repository.getBook(bookId)
            ?: return Resource.Error(IllegalArgumentException("Book $bookId not found"))
        return repository.loadContent(book)
    }
}
