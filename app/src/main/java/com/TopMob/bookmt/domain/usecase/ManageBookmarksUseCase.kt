package com.TopMob.bookmt.domain.usecase

import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Bundles the bookmark/highlight/note operations the reader screen needs into one injectable. */
class ManageBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    fun observe(bookId: Long): Flow<List<Bookmark>> = repository.observeBookmarks(bookId)

    suspend fun add(bookmark: Bookmark): Long = repository.addBookmark(bookmark)

    suspend fun update(bookmark: Bookmark) = repository.updateBookmark(bookmark)

    suspend fun delete(bookmarkId: Long) = repository.deleteBookmark(bookmarkId)
}
