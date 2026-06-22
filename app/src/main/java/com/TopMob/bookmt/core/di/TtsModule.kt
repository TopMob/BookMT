package com.TopMob.bookmt.core.di

import com.TopMob.bookmt.core.common.DispatcherProvider
import com.TopMob.bookmt.data.tts.HeuristicTextAnalyzer
import com.TopMob.bookmt.data.tts.OnnxTtsEngine
import com.TopMob.bookmt.domain.tts.TextAnalyzer
import com.TopMob.bookmt.domain.tts.TtsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTextAnalyzer(dispatchers: DispatcherProvider): TextAnalyzer =
        HeuristicTextAnalyzer(dispatchers)

    @Provides
    @Singleton
    fun provideTtsEngine(dispatchers: DispatcherProvider): TtsEngine = OnnxTtsEngine(dispatchers)
}
