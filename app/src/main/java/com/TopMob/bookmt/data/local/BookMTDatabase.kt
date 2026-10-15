package com.TopMob.bookmt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.TopMob.bookmt.data.local.converter.Converters
import com.TopMob.bookmt.data.local.dao.BookDao
import com.TopMob.bookmt.data.local.dao.BookmarkDao
import com.TopMob.bookmt.data.local.entity.BookEntity
import com.TopMob.bookmt.data.local.entity.BookmarkEntity

/**
 * Room database for BookMT. Schemas are exported to `app/schemas` (configured in build.gradle.kts)
 * so migrations can be authored and validated as the schema evolves. Bump [version] and add a
 * Migration when changing the schema — avoid destructive fallbacks in release builds.
 */
@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BookMTDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN position_anchor TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN listening_position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN listening_anchor TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN content_revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE books SET completed = 1 WHERE progress_percent >= 1")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN start_anchor TEXT")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN end_anchor TEXT")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN content_revision INTEGER NOT NULL DEFAULT 1")
            }
        }
        /** v2 adds editable description, source file size, and accumulated reading-time columns. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN file_size INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN total_reading_time_ms INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
