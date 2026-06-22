package com.TopMob.bookmt.data.repository

import com.TopMob.bookmt.data.settings.AppSettingsDataStore
import com.TopMob.bookmt.domain.model.AppSettings
import com.TopMob.bookmt.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

class AppSettingsRepositoryImpl(
    private val dataStore: AppSettingsDataStore,
) : AppSettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) =
        dataStore.update(transform)
}
