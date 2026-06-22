package com.TopMob.bookmt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.TopMob.bookmt.core.common.Constants
import com.TopMob.bookmt.domain.model.ReaderFontFamily
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.model.ReaderTextAlignment
import com.TopMob.bookmt.domain.model.ReaderTheme
import com.TopMob.bookmt.domain.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Top-level DataStore delegate (one instance per process, keyed by name). */
private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SETTINGS_DATASTORE_NAME,
)

/**
 * Persists [ReaderSettings] in a Preferences DataStore and exposes them as a hot [Flow]. Enum
 * values are stored by name and decoded defensively so a renamed/removed enum can never crash the
 * reader — it falls back to the default.
 */
class ReaderSettingsDataStore(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .catch { e ->
            // DataStore surfaces read failures as IOException; recover with defaults.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs.toReaderSettings() }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val updated = transform(prefs.toReaderSettings())
            prefs.writeReaderSettings(updated)
        }
    }

    private fun Preferences.toReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            theme = decodeEnum(this[Keys.THEME], ReaderTheme.entries, defaults.theme),
            fontSizeSp = this[Keys.FONT_SIZE] ?: defaults.fontSizeSp,
            fontFamily = decodeEnum(this[Keys.FONT_FAMILY], ReaderFontFamily.entries, defaults.fontFamily),
            lineSpacingMultiplier = this[Keys.LINE_SPACING] ?: defaults.lineSpacingMultiplier,
            horizontalMarginDp = this[Keys.MARGIN_H] ?: defaults.horizontalMarginDp,
            verticalMarginDp = this[Keys.MARGIN_V] ?: defaults.verticalMarginDp,
            textAlignment = decodeEnum(this[Keys.ALIGNMENT], ReaderTextAlignment.entries, defaults.textAlignment),
            readingMode = decodeEnum(this[Keys.READING_MODE], ReadingMode.entries, defaults.readingMode),
            brightnessOverrideEnabled = this[Keys.BRIGHTNESS_OVERRIDE] ?: defaults.brightnessOverrideEnabled,
            brightnessLevel = this[Keys.BRIGHTNESS_LEVEL] ?: defaults.brightnessLevel,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        )
    }

    private fun MutablePreferences.writeReaderSettings(s: ReaderSettings) {
        this[Keys.THEME] = s.theme.name
        this[Keys.FONT_SIZE] = s.fontSizeSp
        this[Keys.FONT_FAMILY] = s.fontFamily.name
        this[Keys.LINE_SPACING] = s.lineSpacingMultiplier
        this[Keys.MARGIN_H] = s.horizontalMarginDp
        this[Keys.MARGIN_V] = s.verticalMarginDp
        this[Keys.ALIGNMENT] = s.textAlignment.name
        this[Keys.READING_MODE] = s.readingMode.name
        this[Keys.BRIGHTNESS_OVERRIDE] = s.brightnessOverrideEnabled
        this[Keys.BRIGHTNESS_LEVEL] = s.brightnessLevel
        this[Keys.KEEP_SCREEN_ON] = s.keepScreenOn
    }

    private fun <T : Enum<T>> decodeEnum(name: String?, values: List<T>, default: T): T =
        values.firstOrNull { it.name == name } ?: default

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val MARGIN_H = intPreferencesKey("margin_h")
        val MARGIN_V = intPreferencesKey("margin_v")
        val ALIGNMENT = stringPreferencesKey("alignment")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val BRIGHTNESS_OVERRIDE = booleanPreferencesKey("brightness_override")
        val BRIGHTNESS_LEVEL = floatPreferencesKey("brightness_level")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}
