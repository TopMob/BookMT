package com.TopMob.bookmt.presentation.settings

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.ReaderOrientation
import com.TopMob.bookmt.domain.model.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ExtraReadingPreferences(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val fontPicker = rememberLauncherForActivityResult(com.TopMob.bookmt.presentation.util.LocalOpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val path = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                    val target = File(dir, "${UUID.randomUUID()}.ttf")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { out ->
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 20 * 1024 * 1024) { "Font exceeds 20 MB" }
                                    out.write(buffer, 0, count)
                                }
                            }
                        } ?: error("Cannot open font")
                        Typeface.createFromFile(target) // Reject invalid data before persisting the path.
                        target.absolutePath
                    } catch (e: Exception) { target.delete(); throw e }
                }
                update { it.copy(customFontPath = path) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { importing = false }
        }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        PreferenceToggle(stringResource(R.string.reader_fullscreen), settings.fullScreen) { v -> update { it.copy(fullScreen = v) } }
        PreferenceToggle(stringResource(R.string.reader_volume_keys), settings.volumeKeysEnabled) { v -> update { it.copy(volumeKeysEnabled = v) } }
        PreferenceToggle(stringResource(R.string.reader_keep_awake), settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
        PreferenceToggle(stringResource(R.string.reader_manual_brightness), settings.brightnessOverrideEnabled) { v -> update { it.copy(brightnessOverrideEnabled = v) } }
        if (settings.brightnessOverrideEnabled) Slider(settings.brightnessLevel,
            { v -> update { it.copy(brightnessLevel = v) } }, valueRange = 0.01f..1f)
        Text(stringResource(R.string.reader_vertical_margin, settings.verticalMarginDp))
        Slider(settings.verticalMarginDp.toFloat(), { v -> update { it.copy(verticalMarginDp = v.toInt()) } }, valueRange = 0f..64f)
        Text(stringResource(R.string.reader_orientation))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderOrientation.entries.forEach { orientation ->
                FilterChip(settings.orientation == orientation, { update { it.copy(orientation = orientation) } }, label = {
                    Text(stringResource(when (orientation) {
                        ReaderOrientation.SYSTEM -> R.string.reader_orientation_system
                        ReaderOrientation.PORTRAIT -> R.string.reader_orientation_portrait
                        ReaderOrientation.LANDSCAPE -> R.string.reader_orientation_landscape
                    }))
                })
            }
        }
        Text(stringResource(R.string.reader_auto_interval, settings.autoPageSeconds))
        Slider(settings.autoPageSeconds.toFloat(), { v -> update { it.copy(autoPageSeconds = v.toInt()) } }, valueRange = 5f..180f)
        TextButton(enabled = !importing, onClick = { fontPicker.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.reader_import_font))
        }
        if (settings.customFontPath != null) TextButton(onClick = { update { it.copy(customFontPath = null) } }) {
            Text(stringResource(R.string.reader_reset_font))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
