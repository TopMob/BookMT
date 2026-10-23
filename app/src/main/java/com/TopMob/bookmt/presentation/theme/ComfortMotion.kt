package com.TopMob.bookmt.presentation.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import com.TopMob.bookmt.domain.model.MotionMode

/** Durations are at system scale 1; Compose's duration scale applies exactly once. */
@Immutable
data class ComfortMotion(val mode: MotionMode = MotionMode.SYSTEM, val systemEnabled: Boolean = true) {
    val spatial: Boolean get() = systemEnabled && mode == MotionMode.SYSTEM
    fun duration(normal: Int): Int = when {
        !systemEnabled || mode == MotionMode.OFF -> 0
        mode == MotionMode.MINIMAL -> minOf(normal, 80)
        else -> normal
    }

    fun enterBar(distancePx: Int): EnterTransition = fadeIn(tween(duration(160), easing = EnterEase)) +
        if (spatial) slideInVertically(tween<IntOffset>(160, easing = EnterEase)) { distancePx } else EnterTransition.None

    fun exitBar(distancePx: Int): ExitTransition = fadeOut(tween(duration(120), easing = ExitEase)) +
        if (spatial) slideOutVertically(tween<IntOffset>(120, easing = ExitEase)) { distancePx } else ExitTransition.None

    companion object {
        val StandardEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val EnterEase = CubicBezierEasing(0f, 0f, 0.2f, 1f)
        val ExitEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    }
}

val LocalComfortMotion = staticCompositionLocalOf { ComfortMotion() }
