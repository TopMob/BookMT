package com.TopMob.bookmt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.AppLanguage
import com.TopMob.bookmt.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Top-level DataStore delegate for app-wide settings (one instance per process). */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.APP_SETTINGS_DATASTORE_NAME,
)

/**
 * Persists [AppSettings] in a Preferences DataStore. The language is stored by enum name and
 * decoded defensively so an unknown value falls back to [AppLanguage.SYSTEM].
 */
class AppSettingsDataStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.appDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> AppSettings(language = decodeLanguage(prefs[Keys.LANGUAGE])) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            val current = AppSettings(language = decodeLanguage(prefs[Keys.LANGUAGE]))
            prefs[Keys.LANGUAGE] = transform(current).language.name
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
    }

    companion object {
        private fun decodeLanguage(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.SYSTEM

        /**
         * Reads the persisted language synchronously. Required by `MainActivity.attachBaseContext`,
         * which runs before Hilt injection is available and must localize the context up front.
         */
        fun readLanguageBlocking(context: Context): AppLanguage = runBlocking {
            decodeLanguage(context.appDataStore.data.first()[Keys.LANGUAGE])
        }
    }
}
