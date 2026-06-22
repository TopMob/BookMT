package com.TopMob.bookmt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BookMTDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
}
