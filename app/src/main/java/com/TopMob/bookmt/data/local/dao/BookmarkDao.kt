package com.TopMob.bookmt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.TopMob.bookmt.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY position ASC")
    fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE book_id=:bookId ORDER BY position ASC")
    suspend fun getForBook(bookId: Long): List<BookmarkEntity>

    @Query("UPDATE bookmarks SET position=:start, end_position=:end, start_anchor=:startAnchor, end_anchor=:endAnchor, content_revision=:revision WHERE id=:id")
    suspend fun updateAnchors(id: Long, start: Int, end: Int, startAnchor: String?, endAnchor: String?, revision: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
