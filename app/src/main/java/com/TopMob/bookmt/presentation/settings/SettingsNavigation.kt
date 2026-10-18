package com.TopMob.bookmt.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R

enum class SettingsPage(val title: Int, val icon: ImageVector) {
    HOME(R.string.settings_title, Icons.Filled.Settings),
    APPEARANCE(R.string.appearance_title, Icons.Filled.Palette),
    READING(R.string.settings_reading_controls, Icons.AutoMirrored.Filled.MenuBook),
    SPEECH(R.string.tts_title, Icons.Filled.Headphones),
    FILES(R.string.settings_library_files, Icons.Filled.Folder),
    LANGUAGE(R.string.settings_language, Icons.Filled.Language),
    ABOUT(R.string.settings_about, Icons.Filled.Info),
    STATISTICS(R.string.library_statistics, Icons.Filled.BarChart),
}

@Composable
fun SettingsNavigationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit,
    enabled: Boolean = true, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(icon, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsHome(onOpen: (SettingsPage) -> Unit, readerOnly: Boolean = false) {
    val pages = if (readerOnly) listOf(SettingsPage.APPEARANCE, SettingsPage.READING, SettingsPage.SPEECH)
        else listOf(SettingsPage.APPEARANCE, SettingsPage.READING, SettingsPage.SPEECH, SettingsPage.FILES, SettingsPage.LANGUAGE, SettingsPage.ABOUT)
    pages.forEach { page ->
        SettingsNavigationRow(stringResource(page.title), stringResource(when (page) {
            SettingsPage.APPEARANCE -> R.string.settings_appearance_summary
            SettingsPage.READING -> R.string.settings_reading_summary
            SettingsPage.SPEECH -> R.string.settings_speech_summary
            SettingsPage.FILES -> R.string.settings_files_summary
            SettingsPage.LANGUAGE -> R.string.settings_language_summary
            else -> R.string.settings_about_summary
        }), page.icon, onClick = { onOpen(page) })
    }
}

/** Local dragging previews a value; expensive text layout and persistence happen once on release. */
@Composable
fun CommittedPreferenceSlider(value: Float, range: ClosedFloatingPointRange<Float>,
    label: @Composable (Float) -> String, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    val latestCommit by rememberUpdatedState(onCommit)
    val currentDraft by rememberUpdatedState(draft)
    val persistedValue by rememberUpdatedState(value)
    DisposableEffect(Unit) {
        onDispose { if (currentDraft != persistedValue) latestCommit(currentDraft) }
    }
    Text(label(draft), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
    Slider(draft, { draft = it }, valueRange = range, onValueChangeFinished = { if (draft != value) onCommit(draft) })
}
