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

    @Query("UPDATE books SET uri = :uri, file_size = :size WHERE id = :bookId")
    suspend fun updateSource(bookId: Long, uri: String, size: Long)

    @Query("SELECT COUNT(*) FROM books WHERE cover_image_path = :path")
    suspend fun countByCoverPath(path: String): Int

    @Query("UPDATE books SET cover_image_path=:path WHERE id=:bookId AND (cover_image_path IS NULL OR cover_image_path='')")
    suspend fun setCoverIfMissing(bookId: Long, path: String): Int

    @Update
    suspend fun update(book: BookEntity)

    @Query(
        """
        UPDATE books
        SET progress_percent = :progress,
            last_read_position = :position,
            last_read_timestamp = :timestamp
            , completed = CASE WHEN :progress >= 1 THEN 1 ELSE completed END
            , position_anchor = CASE WHEN :position = 0 THEN NULL ELSE position_anchor END
        WHERE id = :bookId
        """,
    )
    suspend fun updateProgress(bookId: Long, progress: Float, position: Int, timestamp: Long)

    @Query("UPDATE books SET progress_percent=:progress, last_read_position=:position, position_anchor=:anchor, content_revision=:revision, last_read_timestamp=:timestamp, completed=CASE WHEN :progress>=1 THEN 1 ELSE completed END WHERE id=:bookId")
    suspend fun saveReadingPosition(bookId: Long, progress: Float, position: Int, anchor: String?, revision: Int, timestamp: Long)

    @Query("UPDATE books SET listening_position=:position, listening_anchor=:anchor WHERE id=:bookId")
    suspend fun saveListeningPosition(bookId: Long, position: Int, anchor: String?)

    @Query("UPDATE books SET completed=:completed WHERE id=:bookId")
    suspend fun setCompleted(bookId: Long, completed: Boolean)

    // Room applies the registered TypeConverter to the List<String> parameter.
    @Query("UPDATE books SET tags = :tags WHERE id = :bookId")
    suspend fun updateTags(bookId: Long, tags: List<String>)

    @Query(
        """
        UPDATE books
        SET title = :title,
            author = :author,
            description = :description,
            cover_image_path = :coverImagePath,
            tags = :tags
        WHERE id = :bookId
        """,
    )
    suspend fun updateMetadata(
        bookId: Long,
        title: String,
        author: String?,
        description: String?,
        coverImagePath: String?,
        tags: List<String>,
    )

    @Query("UPDATE books SET total_reading_time_ms = total_reading_time_ms + :deltaMs WHERE id = :bookId")
    suspend fun addReadingTime(bookId: Long, deltaMs: Long)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: Long)
}
