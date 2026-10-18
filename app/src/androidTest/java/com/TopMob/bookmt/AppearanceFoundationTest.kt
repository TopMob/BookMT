package com.TopMob.bookmt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.components.ComfortEmptyState
import com.TopMob.bookmt.presentation.settings.ReaderThemePreference
import com.TopMob.bookmt.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppearanceFoundationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemAnimationScaleIsObservedAndNeverMultipliedTwice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val scale = AppMotionScale(context).also { it.start() }
        try {
            shell("settings put global animator_duration_scale 0")
            compose.waitUntil(5000) { scale.systemScale == 0f }
            assertEquals(0f, scale.scaleFactor)
            shell("settings put global animator_duration_scale 2")
            compose.waitUntil(5000) { scale.systemScale == 2f }
            assertEquals(2f, scale.scaleFactor)
            compose.runOnIdle { scale.mode = MotionMode.OFF }
            assertEquals(0f, scale.scaleFactor)
            compose.runOnIdle { scale.mode = MotionMode.MINIMAL }
            assertEquals(0f, scale.scaleFactor)
        } finally {
            shell("settings put global animator_duration_scale $original")
            scale.stop()
        }
    }

    @Test fun readerThemeChoiceIsOwnedByReaderAndRetainsExplicitSelection() {
        val value = mutableStateOf(ReaderSettings().withReaderTheme(ReaderTheme.NIGHT))
        var observedSurface = androidx.compose.ui.graphics.Color.Unspecified
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            BookMTTheme(ReaderTheme.DAY) {
                BookMTTheme(value.value.theme) {
                    val surface = MaterialTheme.colorScheme.surface
                    SideEffect { observedSurface = surface }
                    ReaderThemePreference(value.value) { change -> value.value = change(value.value) }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_theme)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_theme_sepia)).performClick()
        compose.runOnIdle {
            assertEquals(ReaderTheme.SEPIA, value.value.theme)
            assertFalse(value.value.appearance.readerFollowsApp)
            assertEquals(ComfortPalettes.Sepia.surface, observedSurface)
            assertEquals(ReaderTheme.NIGHT, value.value.appearance.lastDarkTheme)
        }
    }

    @Test fun paletteComponentsRemainUsableAtLargeFontScales() {
        val theme = mutableStateOf(ReaderTheme.DAY)
        val fontScale = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue),
                LocalComfortMotion provides ComfortMotion(MotionMode.OFF)) {
                BookMTTheme(theme.value) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text("BookMT — comfortable reading", style = MaterialTheme.typography.titleLarge)
                            ReaderThemePreference(ReaderSettings().withReaderTheme(theme.value)) {}
                            OutlinedTextField("A quiet evening with a book", {}, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                            ComfortEmptyState("Your library", "Add a local book to start reading.",
                                actionLabel = "Add book", onAction = {})
                        }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ux-foundation").apply { mkdirs() }
        for (palette in listOf(ReaderTheme.DAY, ReaderTheme.SEPIA, ReaderTheme.NIGHT, ReaderTheme.AMOLED_BLACK)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { theme.value = palette; fontScale.floatValue = scale }
                compose.waitForIdle()
                compose.onNodeWithText("Add book").performScrollTo().assertIsDisplayed()
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(directory, "${palette.name}-$scale.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }
}
