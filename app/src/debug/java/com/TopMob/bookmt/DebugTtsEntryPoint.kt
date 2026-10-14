package com.TopMob.bookmt

import com.TopMob.bookmt.domain.tts.TtsEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access to the same singleton used by the foreground playback service. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugTtsEntryPoint { fun ttsEngine(): TtsEngine }
