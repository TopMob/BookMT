package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.repository.BookRepository
import javax.inject.Inject

/** Persists the reader's current position/progress (debounced by the caller). */
class UpdateReadingProgressUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(bookId: Long, position: Int, totalLength: Int) {
        val progress = if (totalLength <= 0) 0f else (position.toFloat() / totalLength).coerceIn(0f, 1f)
        repository.updateProgress(bookId, progress, position)
    }
}
