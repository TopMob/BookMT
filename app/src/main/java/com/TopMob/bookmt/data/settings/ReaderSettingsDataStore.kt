package com.TopMob.bookmt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.data.settings.ReaderSettingsCodec.toReaderSettings
import com.TopMob.bookmt.data.settings.ReaderSettingsCodec.writeReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Top-level DataStore delegate (one instance per process, keyed by name). */
private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SETTINGS_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = ReaderSettingsCodec.needsMigration(currentData)
            override suspend fun migrate(currentData: Preferences): Preferences {
                // An existing installation may never have changed a reader setting at all.
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                return ReaderSettingsCodec.migrate(currentData, info.lastUpdateTime > info.firstInstallTime)
            }
            override suspend fun cleanUp() = Unit
        })
    },
)

/**
 * Persists [ReaderSettings] in a Preferences DataStore and exposes them as a hot [Flow]. Enum
 * values are stored by name and decoded defensively so a renamed/removed enum can never crash the
 * reader — it falls back to the default.
 */
class ReaderSettingsDataStore(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .map { prefs -> prefs.toReaderSettings().also { AppearanceLaunchCache.write(context, it.appearance) } }
        .catch { e ->
            // Do not cache a fallback as if it were a successfully persisted choice.
            if (e is IOException) emit(ReaderSettings()) else throw e
        }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val updated = transform(prefs.toReaderSettings()).sanitized()
            prefs.writeReaderSettings(updated)
        }
    }

}
