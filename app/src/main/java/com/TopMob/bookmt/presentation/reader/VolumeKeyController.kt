package com.TopMob.bookmt.presentation.reader

import android.view.KeyEvent

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

