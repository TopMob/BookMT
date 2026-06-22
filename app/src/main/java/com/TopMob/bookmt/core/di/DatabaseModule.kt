package com.TopMob.bookmt.core.di

import android.content.Context
import androidx.room.Room
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.data.local.BookMTDatabase
import com.TopMob.bookmt.data.local.dao.BookDao
import com.TopMob.bookmt.data.local.dao.BookmarkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BookMTDatabase =
        Room.databaseBuilder(context, BookMTDatabase::class.java, Constants.DATABASE_NAME)
            // Foreign keys are needed for the bookmark cascade-delete to fire.
            .addMigrations(BookMTDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideBookDao(db: BookMTDatabase): BookDao = db.bookDao()

    @Provides
    fun provideBookmarkDao(db: BookMTDatabase): BookmarkDao = db.bookmarkDao()
}
