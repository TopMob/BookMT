package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.core.common.Resource
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.repository.BookRepository
import javax.inject.Inject

/** Imports a book file (picked via SAF) into the library. */
class ImportBookUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(uri: String, fileName: String, mimeType: String?): Resource<Book> =
        repository.importBook(uri, fileName, mimeType)
}
