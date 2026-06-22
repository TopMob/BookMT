package com.TopMob.bookmt.core.di

import android.content.ContentResolver
import android.content.Context
import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.local.dao.BookDao
import com.TopMob.bookmt.data.local.dao.BookmarkDao
import com.TopMob.bookmt.data.parser.BookParserFactory
import com.TopMob.bookmt.data.repository.BookRepositoryImpl
import com.TopMob.bookmt.data.repository.BookmarkRepositoryImpl
import com.TopMob.bookmt.data.repository.ReaderSettingsRepositoryImpl
import com.TopMob.bookmt.data.settings.ReaderSettingsDataStore
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.repository.BookmarkRepository
import com.TopMob.bookmt.domain.repository.ReaderSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds domain repository interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideReaderSettingsDataStore(@ApplicationContext context: Context): ReaderSettingsDataStore =
        ReaderSettingsDataStore(context)

    @Provides
    @Singleton
    fun provideBookRepository(
        bookDao: BookDao,
        parserFactory: BookParserFactory,
        contentResolver: ContentResolver,
        dispatchers: DispatcherProvider,
    ): BookRepository = BookRepositoryImpl(bookDao, parserFactory, contentResolver, dispatchers)

    @Provides
    @Singleton
    fun provideBookmarkRepository(
        bookmarkDao: BookmarkDao,
        dispatchers: DispatcherProvider,
    ): BookmarkRepository = BookmarkRepositoryImpl(bookmarkDao, dispatchers)

    @Provides
    @Singleton
    fun provideReaderSettingsRepository(
        dataStore: ReaderSettingsDataStore,
    ): ReaderSettingsRepository = ReaderSettingsRepositoryImpl(dataStore)
}
