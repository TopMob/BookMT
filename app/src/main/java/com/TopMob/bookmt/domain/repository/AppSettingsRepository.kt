package com.TopMob.bookmt.domain.repository

import com.TopMob.bookmt.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Reactive access to persisted app-wide [AppSettings] (DataStore-backed). */
interface AppSettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
