package com.TopMob.bookmt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.TopMob.bookmt.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query(
        """
        UPDATE books
        SET progress_percent = :progress,
            last_read_position = :position,
            last_read_timestamp = :timestamp
        WHERE id = :bookId
        """,
    )
    suspend fun updateProgress(bookId: Long, progress: Float, position: Int, timestamp: Long)

    // Room applies the registered TypeConverter to the List<String> parameter.
    @Query("UPDATE books SET tags = :tags WHERE id = :bookId")
    suspend fun updateTags(bookId: Long, tags: List<String>)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: Long)
}
