package com.TopMob.bookmt.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.*

/** The same readable, full-row choice control can be used in settings and reader sheets. */
@Composable
fun <T> ComfortChoiceRow(title: String, selected: T, options: List<T>, label: @Composable (T) -> String,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { open = true }
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        LazyColumn(Modifier.selectableGroup()) {
            items(options) { value ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = value == selected,
                    role = Role.RadioButton, onClick = { onSelect(value); open = false }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = value == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label(value), modifier = Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } })
}

@Composable
fun AppearancePreferences(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    ComfortChoiceRow(stringResource(R.string.appearance_app_theme), settings.appearance.appTheme,
        AppThemeMode.entries, { appThemeLabel(it) }, { value -> update { it.copy(appearance = it.appearance.copy(appTheme = value)) } })
    ComfortChoiceRow(stringResource(R.string.appearance_motion), settings.appearance.motion,
        MotionMode.entries, { motionLabel(it) }, { value -> update { it.copy(appearance = it.appearance.copy(motion = value)) } })
    Text(stringResource(R.string.appearance_motion_hint), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
fun ReaderThemePreference(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    // null means inheritance; CUSTOM remains readable in old preferences, but has no unfinished editor.
    val selected = if (settings.appearance.readerFollowsApp) null else settings.theme
    val options = listOf<ReaderTheme?>(null) + ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM }
    ComfortChoiceRow(stringResource(R.string.settings_theme), selected, options, { value ->
        if (value == null) stringResource(R.string.appearance_same_as_app) else readerThemeLabel(value)
    }, { value -> update { if (value == null) it.copy(appearance = it.appearance.copy(readerFollowsApp = true)) else it.withReaderTheme(value) } })
}

@Composable
private fun appThemeLabel(value: AppThemeMode): String = if (value == AppThemeMode.SYSTEM)
    stringResource(R.string.appearance_system) else readerThemeLabel(value.resolve(false))

@Composable
private fun motionLabel(value: MotionMode): String = stringResource(when (value) {
    MotionMode.SYSTEM -> R.string.appearance_motion_system
    MotionMode.MINIMAL -> R.string.appearance_motion_minimal
    MotionMode.OFF -> R.string.appearance_motion_off
})

@Composable
private fun readerThemeLabel(value: ReaderTheme): String = stringResource(when (value) {
    ReaderTheme.DAY, ReaderTheme.CUSTOM -> R.string.settings_theme_day
    ReaderTheme.SEPIA -> R.string.settings_theme_sepia
    ReaderTheme.NIGHT -> R.string.settings_theme_night
    ReaderTheme.AMOLED_BLACK -> R.string.settings_theme_amoled
})
