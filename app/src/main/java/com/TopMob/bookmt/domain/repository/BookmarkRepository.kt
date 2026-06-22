package com.TopMob.bookmt.domain.repository

import com.TopMob.bookmt.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/** Manages bookmarks, highlights, and notes for the "Notes & Bookmarks" drawer. */
interface BookmarkRepository {

    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>>

    suspend fun addBookmark(bookmark: Bookmark): Long

    suspend fun updateBookmark(bookmark: Bookmark)

    suspend fun deleteBookmark(bookmarkId: Long)

    suspend fun deleteBookmarksForBook(bookId: Long)
}
