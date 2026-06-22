package com.TopMob.bookmt

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.core.common.LocaleManager
import com.TopMob.bookmt.data.settings.AppSettingsDataStore
import com.TopMob.bookmt.presentation.navigation.BookMTNavHost
import com.TopMob.bookmt.presentation.reader.VolumeKey
import com.TopMob.bookmt.presentation.reader.VolumeKeyController
import com.TopMob.bookmt.presentation.settings.AppSettingsViewModel
import com.TopMob.bookmt.ui.theme.BookMTTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. [AndroidEntryPoint] enables Hilt injection into this activity and the
 * Compose-hosted ViewModels reached through [BookMTNavHost].
 *
 * Implements [VolumeKeyController] so the reader can opt into hardware volume-key page turning
 * while it is on screen; when no handler is registered the keys behave normally.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), VolumeKeyController {

    override var onVolumeKey: ((VolumeKey) -> Boolean)? = null

    /** Localize the whole activity before any view is created, based on the persisted language. */
    override fun attachBaseContext(newBase: Context) {
        val language = AppSettingsDataStore.readLanguageBlocking(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookMTTheme {
                ApplyLanguageChanges()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BookMTNavHost()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val key = keyCode.toVolumeKey()
        if (key != null && onVolumeKey?.invoke(key) == true) {
            // Consuming the DOWN event flips the page and suppresses the system volume HUD.
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // While a reader handler is active, also swallow the UP event so no volume change leaks.
        if (onVolumeKey != null && keyCode.toVolumeKey() != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.toVolumeKey(): VolumeKey? = when (this) {
        KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
        else -> null
    }
}

/**
 * Recreates the activity when the user picks a different language, so the new locale is baked in via
 * [MainActivity.attachBaseContext]. [LocaleManager.applied] is the language currently live, so this
 * only fires on an actual change rather than on every recomposition.
 */
@Composable
private fun ApplyLanguageChanges(
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.language) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        if (LocaleManager.applied != null && state.language != LocaleManager.applied) {
            activity.recreate()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
