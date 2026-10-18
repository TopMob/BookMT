package com.TopMob.bookmt.data.settings

import androidx.datastore.preferences.core.*
import com.TopMob.bookmt.domain.model.*

/** Pure codec: legacy ZIP preferences and new appearance keys share the original file. */
internal object ReaderSettingsCodec {
    internal fun Preferences.toReaderSettings(legacyInstall: Boolean = false): ReaderSettings {
        val defaults = ReaderSettings()
        val legacy = this[Keys.UI_VERSION] == null && (asMap().isNotEmpty() || legacyInstall)
        val savedTheme = decodeEnum(this[Keys.THEME], ReaderTheme.entries, defaults.theme)
        val appearance = AppearanceSettings(
            appTheme = decodeEnum(this[Keys.APP_THEME], AppThemeMode.entries,
                if (legacy) savedTheme.asAppTheme() else AppThemeMode.SYSTEM),
            readerFollowsApp = this[Keys.READER_FOLLOWS_APP] ?: !legacy,
            lastLightTheme = decodeEnum(this[Keys.LIGHT_THEME], ReaderTheme.entries,
                if (savedTheme == ReaderTheme.SEPIA) ReaderTheme.SEPIA else ReaderTheme.DAY),
            lastDarkTheme = decodeEnum(this[Keys.DARK_THEME], ReaderTheme.entries,
                if (savedTheme == ReaderTheme.AMOLED_BLACK) ReaderTheme.AMOLED_BLACK else ReaderTheme.NIGHT),
            motion = decodeEnum(this[Keys.MOTION], MotionMode.entries, MotionMode.SYSTEM),
        ).sanitized()
        return ReaderSettings(
            appearance = appearance,
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
            showProgressSlider = this[Keys.SHOW_SLIDER] ?: defaults.showProgressSlider,
            customFontPath = this[Keys.CUSTOM_FONT_PATH] ?: defaults.customFontPath,
            fullScreen = this[Keys.FULL_SCREEN] ?: defaults.fullScreen,
            volumeKeysEnabled = this[Keys.VOLUME_KEYS] ?: if (legacy) true else defaults.volumeKeysEnabled,
            orientation = decodeEnum(this[Keys.ORIENTATION], ReaderOrientation.entries, defaults.orientation),
            autoPageSeconds = this[Keys.AUTO_PAGE_SECONDS] ?: defaults.autoPageSeconds,
            ttsMaleVoice = this[Keys.TTS_MALE] ?: defaults.ttsMaleVoice,
            ttsSpeed = this[Keys.TTS_SPEED] ?: defaults.ttsSpeed,
            ttsLicenseAccepted = this[Keys.TTS_LICENSE] ?: defaults.ttsLicenseAccepted,
            bookFormatting = this[Keys.BOOK_FORMATTING] ?: defaults.bookFormatting,
            ttsSentencePauseMs = this[Keys.TTS_SENTENCE_PAUSE] ?: defaults.ttsSentencePauseMs,
            ttsParagraphPauseMs = this[Keys.TTS_PARAGRAPH_PAUSE] ?: defaults.ttsParagraphPauseMs,
        ).sanitized()
    }

    fun needsMigration(prefs: Preferences): Boolean = prefs[Keys.UI_VERSION] == null

    fun migrate(prefs: Preferences, legacyInstall: Boolean): Preferences {
        if (!needsMigration(prefs)) return prefs
        return prefs.toMutablePreferences().apply {
            writeReaderSettings(prefs.toReaderSettings(legacyInstall))
        }.toPreferences()
    }

    internal fun MutablePreferences.writeReaderSettings(s: ReaderSettings) {
        this[Keys.UI_VERSION] = 1
        this[Keys.APP_THEME] = s.appearance.appTheme.name
        this[Keys.READER_FOLLOWS_APP] = s.appearance.readerFollowsApp
        this[Keys.LIGHT_THEME] = s.appearance.lastLightTheme.name
        this[Keys.DARK_THEME] = s.appearance.lastDarkTheme.name
        this[Keys.MOTION] = s.appearance.motion.name
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
        this[Keys.SHOW_SLIDER] = s.showProgressSlider
        this[Keys.FULL_SCREEN] = s.fullScreen
        this[Keys.VOLUME_KEYS] = s.volumeKeysEnabled
        this[Keys.ORIENTATION] = s.orientation.name
        this[Keys.AUTO_PAGE_SECONDS] = s.autoPageSeconds
        this[Keys.TTS_MALE] = s.ttsMaleVoice
        this[Keys.TTS_SPEED] = s.ttsSpeed
        this[Keys.TTS_LICENSE] = s.ttsLicenseAccepted
        this[Keys.BOOK_FORMATTING] = s.bookFormatting
        this[Keys.TTS_SENTENCE_PAUSE] = s.ttsSentencePauseMs
        this[Keys.TTS_PARAGRAPH_PAUSE] = s.ttsParagraphPauseMs
        val fontPath = s.customFontPath
        if (fontPath == null) this.remove(Keys.CUSTOM_FONT_PATH) else this[Keys.CUSTOM_FONT_PATH] = fontPath
    }

    private fun <T : Enum<T>> decodeEnum(name: String?, values: List<T>, default: T): T =
        values.firstOrNull { it.name == name } ?: default

    private object Keys {
        val UI_VERSION = intPreferencesKey("comfort_ui_version")
        val APP_THEME = stringPreferencesKey("app_theme")
        val READER_FOLLOWS_APP = booleanPreferencesKey("reader_follows_app")
        val LIGHT_THEME = stringPreferencesKey("last_light_theme")
        val DARK_THEME = stringPreferencesKey("last_dark_theme")
        val MOTION = stringPreferencesKey("motion_mode")
        val BOOK_FORMATTING = booleanPreferencesKey("book_formatting")
        val TTS_SENTENCE_PAUSE = intPreferencesKey("tts_sentence_pause_ms")
        val TTS_PARAGRAPH_PAUSE = intPreferencesKey("tts_paragraph_pause_ms")
        val TTS_MALE = booleanPreferencesKey("tts_male")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_LICENSE = booleanPreferencesKey("supertonic_3_license_accepted")
        val FULL_SCREEN = booleanPreferencesKey("full_screen")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val ORIENTATION = stringPreferencesKey("orientation")
        val AUTO_PAGE_SECONDS = intPreferencesKey("auto_page_seconds")
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
        val SHOW_SLIDER = booleanPreferencesKey("show_slider")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
    }
}
