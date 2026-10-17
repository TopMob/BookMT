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
import kotlinx.coroutines.withTimeoutOrNull
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
        .map { prefs ->
            val lang = decodeLanguage(prefs[Keys.LANGUAGE])
            cachedLanguage = lang
            AppSettings(language = lang)
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { prefs ->
            val current = AppSettings(language = decodeLanguage(prefs[Keys.LANGUAGE]))
            val next = transform(current)
            prefs[Keys.LANGUAGE] = next.language.name
            cachedLanguage = next.language
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_LANGUAGE, next.language.name).apply()
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
    }

    companion object {
        private const val FAST_CACHE_PREFS = "app_settings_fast_cache"
        private const val KEY_CACHED_LANGUAGE = "cached_language"

        @Volatile
        private var cachedLanguage: AppLanguage? = null

        private fun decodeLanguage(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.SYSTEM

        /**
         * Reads the persisted language synchronously without blocking on DataStore coroutine dispatchers.
         * Fast-path uses in-memory volatile cache or direct SharedPreferences.
         * Required by `MainActivity.attachBaseContext`, which runs before Hilt injection is available
         * and must localize the context up front.
         */
        fun readLanguageBlocking(context: Context): AppLanguage {
            cachedLanguage?.let { return it }

            val prefs = runCatching { context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE) }.getOrNull()
            val cachedName = prefs?.getString(KEY_CACHED_LANGUAGE, null)
            if (cachedName != null) {
                val lang = decodeLanguage(cachedName)
                cachedLanguage = lang
                return lang
            }

            // Fallback for migrations or first run before fast cache is written
            val lang = runBlocking {
                try {
                    withTimeoutOrNull(500) {
                        decodeLanguage(context.appDataStore.data.first()[Keys.LANGUAGE])
                    } ?: AppLanguage.SYSTEM
                } catch (_: Exception) {
                    AppLanguage.SYSTEM
                }
            }
            cachedLanguage = lang
            prefs?.edit()?.putString(KEY_CACHED_LANGUAGE, lang.name)?.apply()
            return lang
        }
    }
}
