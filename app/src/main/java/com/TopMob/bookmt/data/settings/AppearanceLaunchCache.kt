package com.TopMob.bookmt.data.settings

import android.content.Context
import com.TopMob.bookmt.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Small derived launch cache, not a second source of user preferences. Cleared on ZIP restore. */
object AppearanceLaunchCache {
    private const val FILE = "appearance_launch_cache"

    fun write(context: Context, value: AppearanceSettings) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val names = mapOf("app" to value.appTheme.name, "motion" to value.motion.name,
            "light" to value.lastLightTheme.name, "dark" to value.lastDarkTheme.name,
            "inherit" to value.readerFollowsApp.toString())
        if (prefs.getInt("ui_version", 0) != 2 || names.any { (key, name) -> prefs.getString(key, null) != name }) {
            prefs.edit().putInt("ui_version", 2).also { editor -> names.forEach { (key, name) -> editor.putString(key, name) } }.apply()
        }
    }

    fun read(context: Context): AppearanceSettings {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val app = if (prefs.getInt("ui_version", 0) == 2) prefs.getString("app", null) else null
        if (app != null) return AppearanceSettings(
            appTheme = AppThemeMode.entries.firstOrNull { it.name == app } ?: AppThemeMode.SYSTEM,
            motion = MotionMode.entries.firstOrNull { it.name == prefs.getString("motion", null) } ?: MotionMode.SYSTEM,
            lastLightTheme = ReaderTheme.entries.firstOrNull { it.name == prefs.getString("light", null) } ?: ReaderTheme.DAY,
            lastDarkTheme = ReaderTheme.entries.firstOrNull { it.name == prefs.getString("dark", null) } ?: ReaderTheme.NIGHT,
            readerFollowsApp = prefs.getString("inherit", "true") != "false",
        ).sanitized()
        // One-time bootstrap for pre-comfort installations; a timeout is never persisted as a choice.
        return runBlocking(Dispatchers.IO) {
            try { withTimeoutOrNull(500) { ReaderSettingsDataStore(context).settings.first().appearance } }
            catch (_: java.io.IOException) { null }
        } ?: AppearanceSettings()
    }

    fun invalidate(context: Context) {
        check(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()) {
            "Cannot invalidate restored appearance cache"
        }
    }
}
