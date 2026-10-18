package com.TopMob.bookmt.domain.model

/** Persisted with reader preferences so existing ZIP backups include the complete appearance. */
data class AppearanceSettings(
    val appTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val readerFollowsApp: Boolean = true,
    val lastLightTheme: ReaderTheme = ReaderTheme.DAY,
    val lastDarkTheme: ReaderTheme = ReaderTheme.NIGHT,
    val motion: MotionMode = MotionMode.SYSTEM,
) {
    fun sanitized() = copy(
        lastLightTheme = lastLightTheme.takeIf { it == ReaderTheme.DAY || it == ReaderTheme.SEPIA } ?: ReaderTheme.DAY,
        lastDarkTheme = lastDarkTheme.takeIf { it == ReaderTheme.NIGHT || it == ReaderTheme.AMOLED_BLACK } ?: ReaderTheme.NIGHT,
    )
}

enum class AppThemeMode { SYSTEM, DAY, SEPIA, NIGHT, BLACK }
enum class MotionMode { SYSTEM, MINIMAL, OFF }

fun AppThemeMode.resolve(systemDark: Boolean): ReaderTheme = when (this) {
    AppThemeMode.SYSTEM -> if (systemDark) ReaderTheme.NIGHT else ReaderTheme.DAY
    AppThemeMode.DAY -> ReaderTheme.DAY
    AppThemeMode.SEPIA -> ReaderTheme.SEPIA
    AppThemeMode.NIGHT -> ReaderTheme.NIGHT
    AppThemeMode.BLACK -> ReaderTheme.AMOLED_BLACK
}

fun ReaderTheme.asAppTheme(): AppThemeMode = when (this) {
    ReaderTheme.DAY, ReaderTheme.CUSTOM -> AppThemeMode.DAY
    ReaderTheme.SEPIA -> AppThemeMode.SEPIA
    ReaderTheme.NIGHT -> AppThemeMode.NIGHT
    ReaderTheme.AMOLED_BLACK -> AppThemeMode.BLACK
}

fun ReaderSettings.resolvedTheme(systemDark: Boolean): ReaderTheme =
    if (appearance.readerFollowsApp) appearance.appTheme.resolve(systemDark) else theme

fun ReaderSettings.withReaderTheme(value: ReaderTheme): ReaderSettings = copy(
    theme = value,
    appearance = appearance.copy(
        readerFollowsApp = false,
        lastLightTheme = if (value == ReaderTheme.DAY || value == ReaderTheme.SEPIA) value else appearance.lastLightTheme,
        lastDarkTheme = if (value == ReaderTheme.NIGHT || value == ReaderTheme.AMOLED_BLACK) value else appearance.lastDarkTheme,
    ),
)
