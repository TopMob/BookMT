package com.TopMob.bookmt

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.TopMob.bookmt.data.importing.DocumentImport
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
import com.TopMob.bookmt.presentation.reader.ReaderNavigationAction
import com.TopMob.bookmt.presentation.reader.VolumeKey
import com.TopMob.bookmt.presentation.reader.VolumeKeyController
import com.TopMob.bookmt.presentation.settings.AppSettingsViewModel
import com.TopMob.bookmt.ui.theme.BookMTTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. [AndroidEntryPoint] enables Hilt injection into this activity and the
 * Compose-hosted ViewModels reached through [BookMTNavHost].
 *
 * Implements [VolumeKeyController] so the reader can opt into hardware volume-key and physical
 * keyboard navigation while it is on screen; when no handler is registered the keys behave normally.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), VolumeKeyController {

    @Inject lateinit var documentImport: DocumentImport
    private var importedBookId by mutableStateOf<Long?>(null)

    override var onVolumeKey: ((VolumeKey) -> Boolean)? = null
    override var onReaderNavigation: ((ReaderNavigationAction, KeyEvent) -> Boolean)? = null

    /** Localize the whole activity before any view is created, based on the persisted language. */
    override fun attachBaseContext(newBase: Context) {
        val language = AppSettingsDataStore.readLanguageBlocking(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) importIntent(intent)
        setContent {
            BookMTTheme {
                ApplyLanguageChanges()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BookMTNavHost(importedBookId = importedBookId, onImportHandled = { importedBookId = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importIntent(intent)
    }

    private fun importIntent(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
            )
            Intent.ACTION_SEND_MULTIPLE ->
                intent?.let { androidx.core.content.IntentCompat.getParcelableArrayListExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }.orEmpty()
            else -> emptyList()
        }.filter { it.scheme == "content" || it.scheme == "file" }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val result = documentImport.import(uris)
            if (result.bookIds.size == 1) importedBookId = result.bookIds.single()
            if (result.failures.isNotEmpty()) android.widget.Toast.makeText(this@MainActivity,
                result.failures.joinToString("\n"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        val vKey = keyCode.toVolumeKey()
        if (vKey != null && onVolumeKey?.invoke(vKey) == true) {
            // Consuming the DOWN event flips the page and suppresses the system volume HUD.
            return true
        }

        val handler = onReaderNavigation
        if (handler != null) {
            val navAction = keyCode.toNavigationAction(event)
            if (navAction != null && handler(navAction, event)) {
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // While a reader handler is active, also swallow the UP event so no volume change leaks.
        if (onVolumeKey != null && keyCode.toVolumeKey() != null) return true
        if (onReaderNavigation != null && keyCode.isNavigationKey()) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.toVolumeKey(): VolumeKey? = when (this) {
        KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
        else -> null
    }

    private fun Int.toNavigationAction(event: KeyEvent): ReaderNavigationAction? = when (this) {
        KeyEvent.KEYCODE_PAGE_DOWN -> ReaderNavigationAction.PAGE_NEXT
        KeyEvent.KEYCODE_PAGE_UP -> ReaderNavigationAction.PAGE_PREVIOUS
        KeyEvent.KEYCODE_SPACE -> if (event.isShiftPressed) ReaderNavigationAction.PAGE_PREVIOUS else ReaderNavigationAction.PAGE_NEXT
        KeyEvent.KEYCODE_DPAD_DOWN -> ReaderNavigationAction.LINE_NEXT
        KeyEvent.KEYCODE_DPAD_UP -> ReaderNavigationAction.LINE_PREVIOUS
        KeyEvent.KEYCODE_DPAD_RIGHT -> ReaderNavigationAction.PAGE_NEXT
        KeyEvent.KEYCODE_DPAD_LEFT -> ReaderNavigationAction.PAGE_PREVIOUS
        else -> null
    }

    private fun Int.isNavigationKey(): Boolean = when (this) {
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_LEFT -> true
        else -> false
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
