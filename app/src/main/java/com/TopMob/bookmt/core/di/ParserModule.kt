package com.TopMob.bookmt.core.di

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.parser.BookParserFactory
import com.TopMob.bookmt.data.parser.Fb2BookParser
import com.TopMob.bookmt.data.parser.MarkdownBookParser
import com.TopMob.bookmt.data.parser.PdfBookParser
import com.TopMob.bookmt.data.parser.TxtBookParser
import com.TopMob.bookmt.domain.parser.BookParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Contributes each format parser into a multibound `Set<BookParser>` and exposes the
 * [BookParserFactory] over it. Supporting a new format = add one `@Provides @IntoSet` line.
 */
@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    @IntoSet
    fun provideTxtParser(dispatchers: DispatcherProvider): BookParser = TxtBookParser(dispatchers)

    @Provides
    @IntoSet
    fun provideMarkdownParser(dispatchers: DispatcherProvider): BookParser =
        MarkdownBookParser(dispatchers)

    @Provides
    @IntoSet
    fun provideFb2Parser(dispatchers: DispatcherProvider): BookParser = Fb2BookParser(dispatchers)

    @Provides
    @IntoSet
    fun providePdfParser(dispatchers: DispatcherProvider): BookParser = PdfBookParser(dispatchers)

    @Provides
    @Singleton
    fun provideParserFactory(parsers: Set<@JvmSuppressWildcards BookParser>): BookParserFactory =
        BookParserFactory(parsers)
}
