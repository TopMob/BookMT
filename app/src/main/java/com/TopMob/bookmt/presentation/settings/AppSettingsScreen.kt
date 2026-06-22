package com.TopMob.bookmt.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.AppLanguage
import com.TopMob.bookmt.domain.model.ReaderFontFamily
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.model.ReaderTextAlignment
import com.TopMob.bookmt.domain.model.ReaderTheme
import java.io.File

/**
 * Dedicated, app-wide settings screen — reached from the bookshelf top bar. Layout-specific reading
 * preferences stay in the reader's own sheet; this hosts the global ones (currently the UI
 * language). Selecting a language persists it and triggers an activity recreate via the locale
 * effect in `MainActivity`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportMessage = stringResource(R.string.backup_exported)
    val importMessage = stringResource(R.string.backup_imported)
    val failMessage = stringResource(R.string.backup_failed)

    // Export: write a ZIP to a user-chosen location. Import: read one and restart to reload data.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.onExportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onImportBackup(uri) }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Note: Font importing logic needs to be implemented in ViewModel if missing,
        // for now we'll just omit custom font picking from global settings to keep it simple,
        // or we could add it if we have copyFontToInternal available.
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BackupEvent.Exported -> snackbarHostState.showSnackbar(exportMessage)
                BackupEvent.Imported -> restartApp(context)
                is BackupEvent.Failed ->
                    snackbarHostState.showSnackbar(event.message ?: failMessage)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    LanguageRow(
                        language = language,
                        selected = language == state.language,
                        onSelect = { viewModel.onLanguageChange(language) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val rs = state.readerSettings
            val onUpdate = viewModel::onUpdateReaderSettings

            SectionLabel(stringResource(R.string.settings_theme))
            ChipRow(
                options = ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM },
                selected = rs.theme,
                label = { it.label() },
                onSelect = { theme -> onUpdate { it.copy(theme = theme) } },
            )

            SectionLabel(stringResource(R.string.settings_font_size, rs.fontSizeSp.toInt()))
            Slider(
                value = rs.fontSizeSp,
                onValueChange = { v -> onUpdate { it.copy(fontSizeSp = v) } },
                valueRange = ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            SectionLabel(stringResource(R.string.settings_line_spacing, "%.1f".format(rs.lineSpacingMultiplier)))
            Slider(
                value = rs.lineSpacingMultiplier,
                onValueChange = { v -> onUpdate { it.copy(lineSpacingMultiplier = v) } },
                valueRange = ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            SectionLabel(stringResource(R.string.settings_margins, rs.horizontalMarginDp))
            Slider(
                value = rs.horizontalMarginDp.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(horizontalMarginDp = v.toInt()) } },
                valueRange = 0f..64f,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            SectionLabel(stringResource(R.string.settings_font))
            ChipRow(
                options = ReaderFontFamily.entries,
                selected = rs.fontFamily,
                label = { it.label() },
                onSelect = { family -> onUpdate { it.copy(fontFamily = family) } },
            )

            SectionLabel(stringResource(R.string.settings_alignment))
            ChipRow(
                options = ReaderTextAlignment.entries,
                selected = rs.textAlignment,
                label = { it.label() },
                onSelect = { align -> onUpdate { it.copy(textAlignment = align) } },
            )

            SectionLabel(stringResource(R.string.reader_show_slider))
            ToggleRow(
                label = stringResource(R.string.reader_show_slider_desc),
                checked = rs.showProgressSlider,
                onCheckedChange = { on -> onUpdate { it.copy(showProgressSlider = on) } },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { exportLauncher.launch(DEFAULT_BACKUP_NAME) }) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }) {
                    Text(stringResource(R.string.backup_import))
                }
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

/** Relaunches the app so Room/DataStore re-open the freshly restored files. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

private const val DEFAULT_BACKUP_NAME = "bookmt-backup.zip"
private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(language.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.RUSSIAN -> R.string.language_russian
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text = text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.DAY -> stringResource(R.string.settings_theme_day)
    ReaderTheme.SEPIA -> stringResource(R.string.settings_theme_sepia)
    ReaderTheme.NIGHT -> stringResource(R.string.settings_theme_night)
    ReaderTheme.AMOLED_BLACK -> stringResource(R.string.settings_theme_amoled)
    ReaderTheme.CUSTOM -> stringResource(R.string.settings_theme_custom)
}

@Composable
private fun ReaderFontFamily.label(): String = when (this) {
    ReaderFontFamily.SYSTEM -> stringResource(R.string.settings_font_system)
    ReaderFontFamily.SERIF -> "Serif"
    ReaderFontFamily.SANS_SERIF -> "Sans Serif"
    ReaderFontFamily.MONOSPACE -> "Monospace"
}

@Composable
private fun ReaderTextAlignment.label(): String = when (this) {
    ReaderTextAlignment.START -> stringResource(R.string.settings_alignment_start)
    ReaderTextAlignment.CENTER -> stringResource(R.string.settings_alignment_center)
    ReaderTextAlignment.JUSTIFY -> stringResource(R.string.settings_alignment_justify)
}
