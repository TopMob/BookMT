package com.TopMob.bookmt.data.repository

import com.TopMob.bookmt.data.settings.ReaderSettingsDataStore
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.repository.ReaderSettingsRepository
import kotlinx.coroutines.flow.Flow

class ReaderSettingsRepositoryImpl(
    private val dataStore: ReaderSettingsDataStore,
) : ReaderSettingsRepository {

    override val settings: Flow<ReaderSettings> = dataStore.settings

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) =
        dataStore.update(transform)
}
