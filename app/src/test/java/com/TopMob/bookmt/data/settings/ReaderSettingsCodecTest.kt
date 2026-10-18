package com.TopMob.bookmt.data.settings

import androidx.datastore.preferences.core.*
import com.TopMob.bookmt.data.settings.ReaderSettingsCodec.toReaderSettings
import com.TopMob.bookmt.data.settings.ReaderSettingsCodec.writeReaderSettings
import com.TopMob.bookmt.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ReaderSettingsCodecTest {
    @Test fun `updated installation with no saved settings retains old reader defaults`() {
        val migrated = ReaderSettingsCodec.migrate(emptyPreferences(), legacyInstall = true).toReaderSettings()
        assertEquals(AppThemeMode.DAY, migrated.appearance.appTheme)
        assertTrue(migrated.appearance.readerFollowsApp)
        assertTrue(migrated.volumeKeysEnabled)
    }

    @Test fun `fresh initialization is durable and is not mistaken for a legacy install on next update`() {
        val first = ReaderSettingsCodec.migrate(emptyPreferences(), legacyInstall = false)
        assertFalse(ReaderSettingsCodec.needsMigration(first))
        assertEquals(first, ReaderSettingsCodec.migrate(first, legacyInstall = true))
        assertTrue(first.toReaderSettings().appearance.readerFollowsApp)
        assertFalse(first.toReaderSettings().volumeKeysEnabled)
    }
    @Test fun `fresh install follows system without capturing volume keys`() {
        val settings = emptyPreferences().toReaderSettings()
        assertEquals(AppThemeMode.SYSTEM, settings.appearance.appTheme)
        assertTrue(settings.appearance.readerFollowsApp)
        assertFalse(settings.volumeKeysEnabled)
        assertEquals(ReaderTheme.NIGHT, settings.resolvedTheme(systemDark = true))
    }

    @Test fun `legacy preferences retain theme typography speech and hardware behavior on first edit`() {
        val old = mutablePreferencesOf(
            stringPreferencesKey("theme") to "SEPIA",
            floatPreferencesKey("font_size_sp") to 27f,
            intPreferencesKey("margin_h") to 31,
            floatPreferencesKey("tts_speed") to 1.25f,
            intPreferencesKey("tts_sentence_pause_ms") to 220,
            stringPreferencesKey("custom_font_path") to "/local/font.otf",
            stringPreferencesKey("unknown_future_key") to "retain-me",
        )
        val before = old.toReaderSettings()
        assertEquals(AppThemeMode.SEPIA, before.appearance.appTheme)
        assertFalse(before.appearance.readerFollowsApp)
        assertTrue(before.volumeKeysEnabled) // old default applies even if old archive omitted the key
        old.writeReaderSettings(before.copy(appearance = before.appearance.copy(motion = MotionMode.OFF)))
        val after = old.toReaderSettings()
        assertEquals(before.copy(appearance = before.appearance.copy(motion = MotionMode.OFF)), after)
        assertEquals("retain-me", old[stringPreferencesKey("unknown_future_key")])
        assertEquals(2, old[intPreferencesKey("comfort_ui_version")])
        assertEquals(ReaderTheme.SEPIA, after.resolvedTheme(systemDark = true))
    }

    @Test fun `legacy night and black are not replaced by a light system theme`() {
        for ((theme, app) in listOf(ReaderTheme.NIGHT to AppThemeMode.NIGHT, ReaderTheme.AMOLED_BLACK to AppThemeMode.BLACK)) {
            val decoded = preferencesOf(stringPreferencesKey("theme") to theme.name).toReaderSettings()
            assertEquals(app, decoded.appearance.appTheme)
            assertEquals(theme, decoded.resolvedTheme(false))
        }
    }

    @Test fun `new fields round trip in original backup preference file`() {
        val original = ReaderSettings(fontSizeSp = 23f, fullScreen = true, ttsMaleVoice = true)
            .withReaderTheme(ReaderTheme.AMOLED_BLACK).copy(appearance = AppearanceSettings(
                appTheme = AppThemeMode.SEPIA, readerFollowsApp = false,
                lastLightTheme = ReaderTheme.SEPIA, lastDarkTheme = ReaderTheme.AMOLED_BLACK, motion = MotionMode.MINIMAL))
        val prefs = mutablePreferencesOf()
        prefs.writeReaderSettings(original)
        assertEquals(original, prefs.toReaderSettings())
    }

    @Test fun `turning inheritance on retains explicit theme for later reuse`() {
        val explicit = ReaderSettings().withReaderTheme(ReaderTheme.SEPIA)
        val inherited = explicit.copy(appearance = explicit.appearance.copy(readerFollowsApp = true, appTheme = AppThemeMode.NIGHT))
        assertEquals(ReaderTheme.NIGHT, inherited.resolvedTheme(false))
        assertEquals(ReaderTheme.SEPIA, inherited.theme)
        assertEquals(ReaderTheme.SEPIA, inherited.appearance.lastLightTheme)
    }


    @Test fun `version one explicit reader palette becomes the single app palette without losing settings`() {
        val old = mutablePreferencesOf(intPreferencesKey("comfort_ui_version") to 1,
            stringPreferencesKey("app_theme") to "NIGHT", stringPreferencesKey("theme") to "SEPIA",
            booleanPreferencesKey("reader_follows_app") to false, floatPreferencesKey("font_size_sp") to 29f,
            stringPreferencesKey("unknown_future_key") to "keep")
        val migrated = ReaderSettingsCodec.migrate(old, legacyInstall = true)
        val settings = migrated.toReaderSettings()
        assertEquals(AppThemeMode.SEPIA, settings.appearance.appTheme)
        assertTrue(settings.appearance.readerFollowsApp)
        assertEquals(ReaderTheme.SEPIA, settings.resolvedTheme(true))
        assertEquals(29f, settings.fontSizeSp)
        assertEquals("keep", migrated[stringPreferencesKey("unknown_future_key")])
        assertEquals(migrated, ReaderSettingsCodec.migrate(migrated, legacyInstall = true))
    }

    @Test fun `quick reader theme changes also change application palette`() {
        val selected = ReaderSettings().withReaderTheme(ReaderTheme.AMOLED_BLACK)
        assertEquals(AppThemeMode.BLACK, selected.appearance.appTheme)
        assertTrue(selected.appearance.readerFollowsApp)
        assertEquals(ReaderTheme.AMOLED_BLACK, selected.resolvedTheme(false))
        assertEquals(AppThemeMode.SYSTEM, selected.withAppTheme(AppThemeMode.SYSTEM).appearance.appTheme)
    }

    @Test fun `invalid saved enum names and invalid light dark pairs are sanitized`() {
        val prefs = preferencesOf(intPreferencesKey("comfort_ui_version") to 1,
            stringPreferencesKey("app_theme") to "REMOVED", stringPreferencesKey("motion_mode") to "UNKNOWN",
            stringPreferencesKey("last_light_theme") to "AMOLED_BLACK", stringPreferencesKey("last_dark_theme") to "DAY")
        assertEquals(AppearanceSettings(), prefs.toReaderSettings().appearance)
    }
}
