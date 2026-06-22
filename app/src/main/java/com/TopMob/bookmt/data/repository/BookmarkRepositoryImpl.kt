package com.TopMob.bookmt.data.repository

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.local.dao.BookmarkDao
import com.TopMob.bookmt.data.mapper.toDomain
import com.TopMob.bookmt.data.mapper.toEntity
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookmarkRepositoryImpl(
    private val bookmarkDao: BookmarkDao,
    private val dispatchers: DispatcherProvider,
    private val now: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {

    override fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> =
        bookmarkDao.observeForBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun addBookmark(bookmark: Bookmark): Long = withContext(dispatchers.io) {
        val stamped = if (bookmark.timestamp == 0L) bookmark.copy(timestamp = now()) else bookmark
        bookmarkDao.insert(stamped.toEntity())
    }

    override suspend fun updateBookmark(bookmark: Bookmark) = withContext(dispatchers.io) {
        bookmarkDao.update(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmarkId: Long) = withContext(dispatchers.io) {
        bookmarkDao.deleteById(bookmarkId)
    }

    override suspend fun deleteBookmarksForBook(bookId: Long) = withContext(dispatchers.io) {
        bookmarkDao.deleteForBook(bookId)
    }
}
