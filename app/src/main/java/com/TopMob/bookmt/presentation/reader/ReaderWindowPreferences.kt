package com.TopMob.bookmt.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.TopMob.bookmt.domain.model.ReaderOrientation

@Composable
fun ReaderWindowPreferences(fullScreen: Boolean, orientation: ReaderOrientation) {
    val activity = LocalContext.current.findActivity() ?: return
    val view = LocalView.current
    DisposableEffect(activity, view, fullScreen) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val oldBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullScreen) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = oldBehavior
        }
    }
    DisposableEffect(activity, orientation) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = when (orientation) {
            ReaderOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { activity.requestedOrientation = previous }
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun BrightnessOverride(enabled: Boolean, level: Float) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, level) {
        val window = activity?.window
        val attrs = window?.attributes
        val original = attrs?.screenBrightness
        if (window != null && attrs != null && enabled) {
            attrs.screenBrightness = level.coerceIn(0.01f, 1f)
            window.attributes = attrs
        }
        onDispose {
            if (window != null && attrs != null && original != null) {
                attrs.screenBrightness = original
                window.attributes = attrs
            }
        }
    }
}

/** Thin left-edge gesture region: dragging up brightens, down dims (relative to height). */
@Composable
fun BrightnessEdgeStrip(level: Float, onLevelChange: (Float) -> Unit) {
    val currentLevel by rememberUpdatedState(level)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(32.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    val delta = -dragAmount / size.height
                    onLevelChange((currentLevel + delta).coerceIn(0.01f, 1f))
                }
            },
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
