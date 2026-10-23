package com.TopMob.bookmt.presentation.reader

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Which hardware volume key was pressed, as far as page navigation is concerned. */
enum class VolumeKey { UP, DOWN }

/** High-level navigation actions triggered by physical keyboard or controller keys. */
enum class ReaderNavigationAction {
    PAGE_PREVIOUS,
    PAGE_NEXT,
    LINE_PREVIOUS,
    LINE_NEXT,
}

/**
 * Implemented by the host activity so the reader can intercept hardware keys (volume keys,
 * Page Up/Down, Space, D-Pad/Arrows) for navigation while it is on screen.
 */
interface VolumeKeyController {
    var onVolumeKey: ((VolumeKey) -> Boolean)?
    var onReaderNavigation: ((ReaderNavigationAction, KeyEvent) -> Boolean)?
}

/**
 * Attaches navigation listeners to the host [VolumeKeyController] for the lifetime of the reader.
 */
@Composable
fun ReaderKeyNavigation(
    volumeEnabled: Boolean,
    navigationEnabled: Boolean,
    onVolumeKey: (VolumeKey) -> Boolean,
    onNavigate: (ReaderNavigationAction, KeyEvent) -> Boolean,
) {
    val activity = LocalContext.current.findActivity()
    val latestOnVolumeKey by rememberUpdatedState(onVolumeKey)
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    DisposableEffect(activity, volumeEnabled, navigationEnabled) {
        val controller = activity as? VolumeKeyController
        controller?.onVolumeKey = if (volumeEnabled) { key -> latestOnVolumeKey(key) } else null
        controller?.onReaderNavigation = if (navigationEnabled) { action, event -> latestOnNavigate(action, event) } else null
        onDispose {
            controller?.onVolumeKey = null
            controller?.onReaderNavigation = null
        }
    }
}

/**
 * Horizontal tap regions on the paged reader: left third = previous page, right third = next page,
 * center third = toggle the reading controls (Moon+ Reader default tap behavior).
 */
enum class ReaderTapZone {
    LEFT, CENTER, RIGHT;

    companion object {
        private const val LEFT_BOUND = 0.33f
        private const val RIGHT_BOUND = 0.66f

        fun fromX(x: Float, width: Float): ReaderTapZone {
            if (width <= 0f) return CENTER
            return when (x / width) {
                in 0f..LEFT_BOUND -> LEFT
                in RIGHT_BOUND..1f -> RIGHT
                else -> CENTER
            }
        }
    }
}
