package com.TopMob.bookmt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotated with [HiltAndroidApp] to trigger Hilt's code generation,
 * creating the application-level dependency container that every other Hilt component derives from.
 */
@HiltAndroidApp
class BookMTApplication : Application()
