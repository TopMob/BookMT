package com.TopMob.bookmt.core.common

/** Centralized, compile-time constants shared across layers. */
object Constants {
    const val DATABASE_NAME = "bookmt.db"
    const val SETTINGS_DATASTORE_NAME = "reader_settings"
    const val APP_SETTINGS_DATASTORE_NAME = "app_settings"

    /**
     * Size (in characters) of a single rendering "page slice" used by the paginated reader. Keeping
     * slices bounded means Compose only ever lays out a small, constant amount of text per frame,
     * which is the key to lag-free rendering on very large books.
     */
    const val READER_PAGE_CHAR_BUDGET = 2_400

    /** Max characters fed to the [com.TopMob.bookmt.domain.tts.TextAnalyzer] in a single batch. */
    const val ANALYZER_BATCH_CHAR_BUDGET = 4_000

    /** Default asset-relative directory where bundled .onnx voice models are expected to live. */
    const val VOICE_MODELS_DIR = "voices"
}
