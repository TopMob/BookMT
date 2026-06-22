package com.TopMob.bookmt.core.common

import android.content.Context
import android.content.res.Configuration
import com.TopMob.bookmt.domain.model.AppLanguage
import java.util.Locale

/**
 * Applies the user-selected [AppLanguage] to a [Context] without requiring AppCompat. Used from
 * `MainActivity.attachBaseContext` to localize the whole activity, and as the single source of
 * truth for which language is currently live ([applied]) so the activity only recreates on a real
 * change.
 */
object LocaleManager {

    /** The language currently baked into the running activity's resources. Set during attach. */
    @Volatile
    var applied: AppLanguage? = null
        private set

    /** Wraps [context] so its resources resolve against [language]; records it as [applied]. */
    fun wrap(context: Context, language: AppLanguage): Context {
        applied = language
        val locale = language.toLocaleOrNull() ?: return context
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
