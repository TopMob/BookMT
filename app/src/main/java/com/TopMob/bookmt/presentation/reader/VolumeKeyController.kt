package com.TopMob.bookmt.presentation.reader

/** Which hardware volume key was pressed, as far as page navigation is concerned. */
enum class VolumeKey { UP, DOWN }

/**
 * Implemented by the host activity so the reader can intercept hardware volume keys for page
 * turning while it is on screen. The reader registers [onVolumeKey] in a [DisposableEffect] and
 * clears it on dispose; a null handler means the keys keep their default (volume) behavior.
 *
 * The handler returns true when it consumed the press (so the activity suppresses the volume HUD).
 */
interface VolumeKeyController {
    var onVolumeKey: ((VolumeKey) -> Boolean)?
}
