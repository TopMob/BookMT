package com.TopMob.bookmt.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material.icons.filled.*
import com.TopMob.bookmt.presentation.util.formatDuration
import com.TopMob.bookmt.presentation.util.formatFileSize
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
import com.TopMob.bookmt.domain.model.ReadingMode
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
    libraryModel: LibrarySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val summary by libraryModel.summary.collectAsStateWithLifecycle()
    val progress by libraryModel.progress.collectAsStateWithLifecycle()
    val libraryError by libraryModel.error.collectAsStateWithLifecycle()
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.HOME.name) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var license by rememberSaveable { mutableStateOf(false) }
    val page = SettingsPage.valueOf(pageName)
    val holder = rememberSaveableStateHolder()
    BackHandler(page != SettingsPage.HOME) { pageName = SettingsPage.HOME.name }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportMessage = stringResource(R.string.backup_exported)
    val importMessage = stringResource(R.string.backup_imported)
    val failMessage = stringResource(R.string.backup_failed)

    // Export: write a ZIP to a user-chosen location. Import: read one and restart to reload data.
    val exportLauncher = rememberLauncherForActivityResult(
        com.TopMob.bookmt.presentation.util.LocalCreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.onExportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        com.TopMob.bookmt.presentation.util.LocalOpenDocument(),
    ) { uri -> if (uri != null) restoreUri = uri.toString() }

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

    LaunchedEffect(libraryError) {
        libraryError?.let { snackbarHostState.showSnackbar(it); libraryModel.dismissError() }
    }
    if (restoreUri != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { restoreUri = null }, title = { Text(stringResource(R.string.settings_restore_confirm)) },
        text = { Text(stringResource(R.string.settings_restore_explanation)) },
        confirmButton = { TextButton(onClick = { val uri = restoreUri; restoreUri = null
            uri?.let { viewModel.onImportBackup(android.net.Uri.parse(it)) } }) { Text(stringResource(R.string.settings_restore_action)) } },
        dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.action_cancel)) } })
    if (license) TtsLicenseDialog({ license = false }) { license = false }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Text(stringResource(page.title)) }, navigationIcon = {
            IconButton(onClick = { if (page == SettingsPage.HOME) onNavigateBack() else pageName = SettingsPage.HOME.name }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        })
    }) { padding ->
        holder.SaveableStateProvider(pageName) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                if (!state.ready) com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                else when (page) {
                    SettingsPage.HOME -> SettingsHome({ pageName = it.name })
                    SettingsPage.APPEARANCE -> AppearancePreferences(state.readerSettings, viewModel::onUpdateReaderSettings)
                    SettingsPage.READING -> ReadingPreferences(state.readerSettings, viewModel::onUpdateReaderSettings)
                    SettingsPage.SPEECH -> Column(Modifier.padding(horizontal = 20.dp)) { TtsPreferences(state.readerSettings, viewModel::onUpdateReaderSettings) }
                    SettingsPage.LANGUAGE -> Column(Modifier.selectableGroup()) {
                        AppLanguage.entries.forEach { language -> LanguageRow(language, language == state.language) { viewModel.onLanguageChange(language) } }
                    }
                    SettingsPage.FILES -> {
                        Text(stringResource(R.string.settings_backup_desc), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(enabled = !backupBusy, onClick = { exportLauncher.launch(DEFAULT_BACKUP_NAME) }) { Text(stringResource(R.string.backup_export)) }
                            OutlinedButton(enabled = !backupBusy, onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }) { Text(stringResource(R.string.backup_import)) }
                            if (backupBusy) com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator()
                        }
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        SettingsNavigationRow(stringResource(R.string.offline_prepare_library), stringResource(R.string.offline_prepare_hint),
                            Icons.Filled.Folder, enabled = summary?.count?.let { it > 0 } == true && progress == null,
                            onClick = libraryModel::prepareOffline)
                        progress?.let { Text(stringResource(R.string.library_import_progress, it.completed, it.total), Modifier.padding(horizontal = 20.dp)) }
                        SettingsNavigationRow(stringResource(R.string.library_statistics), "", Icons.Filled.BarChart, { pageName = SettingsPage.STATISTICS.name })
                    }
                    SettingsPage.STATISTICS -> LibraryStatistics(summary)
                    SettingsPage.ABOUT -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("BookMT", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.settings_version, com.TopMob.bookmt.BuildConfig.VERSION_NAME))
                        Text(stringResource(R.string.settings_offline_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { license = true }) { Text(stringResource(R.string.tts_license)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStatistics(value: LibrarySummary?) {
    if (value == null) { com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator(); return }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.library_stats_books, value.count, value.finished, value.reading))
        Text(stringResource(R.string.library_stats_time, value.time / 3600000, value.time / 60000 % 60))
        Text(formatFileSize(value.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        value.top.forEach { book ->
            HorizontalDivider()
            Text(book.title, style = MaterialTheme.typography.titleSmall)
            Text(formatDuration(book.totalReadingTimeMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Relaunches the app so Room/DataStore re-open the freshly restored files. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    // A separate task/process start avoids losing the relaunch when the old process exits.
    val intent = Intent.makeRestartActivityTask(launch.component)
    val pending = android.app.PendingIntent.getActivity(context, 0, intent,
        android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    alarms.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, pending)
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
    ReaderFontFamily.SERIF -> stringResource(R.string.settings_serif)
    ReaderFontFamily.SANS_SERIF -> stringResource(R.string.settings_sans)
    ReaderFontFamily.MONOSPACE -> stringResource(R.string.settings_mono)
}

@Composable
private fun ReaderTextAlignment.label(): String = when (this) {
    ReaderTextAlignment.START -> stringResource(R.string.settings_alignment_start)
    ReaderTextAlignment.CENTER -> stringResource(R.string.settings_alignment_center)
    ReaderTextAlignment.JUSTIFY -> stringResource(R.string.settings_alignment_justify)
}

@Composable
fun ReadingPreferences(rs: ReaderSettings, onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    ComfortChoiceRow(stringResource(R.string.settings_reading_mode), rs.readingMode, ReadingMode.entries,
        { stringResource(if (it == ReadingMode.PAGED) R.string.settings_mode_paged else R.string.settings_mode_scroll) },
        { mode -> onUpdate { it.copy(readingMode = mode) } })
    Column(Modifier.padding(horizontal = 20.dp)) {
        CommittedPreferenceSlider(rs.fontSizeSp, ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
            { stringResource(R.string.settings_font_size, it.toInt()) }) { value -> onUpdate { it.copy(fontSizeSp = value) } }
        CommittedPreferenceSlider(rs.lineSpacingMultiplier, ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
            { stringResource(R.string.settings_line_spacing, "%.1f".format(it)) }) { value -> onUpdate { it.copy(lineSpacingMultiplier = value) } }
        CommittedPreferenceSlider(rs.horizontalMarginDp.toFloat(), 0f..64f,
            { stringResource(R.string.settings_margins, it.toInt()) }) { value -> onUpdate { it.copy(horizontalMarginDp = value.toInt()) } }
    }
    ComfortChoiceRow(stringResource(R.string.settings_font), rs.fontFamily, ReaderFontFamily.entries,
        { it.label() }, { family -> onUpdate { it.copy(fontFamily = family, customFontPath = null) } })
    ComfortChoiceRow(stringResource(R.string.settings_alignment), rs.textAlignment, ReaderTextAlignment.entries,
        { it.label() }, { align -> onUpdate { it.copy(textAlignment = align) } })
    Column(Modifier.padding(horizontal = 20.dp)) {
        PreferenceToggle(stringResource(R.string.reader_show_slider_desc), rs.showProgressSlider) { value -> onUpdate { it.copy(showProgressSlider = value) } }
    }
    ExtraReadingPreferences(rs, onUpdate)
}
