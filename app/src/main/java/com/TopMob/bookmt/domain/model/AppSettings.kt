package com.TopMob.bookmt.domain.model

import java.util.Locale

/**
 * App-wide (non-reader) preferences. Kept deliberately separate from [ReaderSettings]: the reader
 * sheet only configures how a page is laid out, while these settings affect the whole application.
 *
 * @property language UI language. [AppLanguage.SYSTEM] follows the device locale.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
)

/** Supported UI languages. [tag] is a BCP-47 language tag; empty means "follow the system". */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru");

    /** The concrete [Locale] to apply, or null when the system default should be used. */
    fun toLocaleOrNull(): Locale? = if (tag.isEmpty()) null else Locale(tag)
}
