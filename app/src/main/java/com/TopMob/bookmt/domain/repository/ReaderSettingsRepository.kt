package com.TopMob.bookmt.domain.repository

import com.TopMob.bookmt.domain.model.ReaderSettings
import kotlinx.coroutines.flow.Flow

/** Reactive access to persisted [ReaderSettings] (DataStore-backed). */
interface ReaderSettingsRepository {

    val settings: Flow<ReaderSettings>

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
}
