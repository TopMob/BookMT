package com.TopMob.bookmt.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import com.TopMob.bookmt.domain.model.MotionMode

/** Window recomposer scale: app OFF also disables framework animations in menus/dialogs. */
class AppMotionScale(private val context: Context) : MotionDurationScale {
    var systemScale by mutableFloatStateOf(readSystemScale())
        private set
    var mode by androidx.compose.runtime.mutableStateOf(MotionMode.SYSTEM)
    // Material3 1.4 has no public motion scheme override. In reduced modes snap its
    // internal spatial effects too, rather than leaving an unexpected menu zoom.
    override val scaleFactor: Float get() = if (mode != MotionMode.SYSTEM) 0f else systemScale

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { systemScale = readSystemScale() }
    }

    fun start() = context.contentResolver.registerContentObserver(
        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
    fun stop() = context.contentResolver.unregisterContentObserver(observer)

    private fun readSystemScale(): Float = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f).takeIf { it.isFinite() && it >= 0f } ?: 1f
}
