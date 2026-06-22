package com.TopMob.bookmt.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.TopMob.bookmt.core.common.LocaleManager
import com.TopMob.bookmt.data.backup.BackupManager
import com.TopMob.bookmt.domain.model.AppLanguage
import com.TopMob.bookmt.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes app-wide settings (UI language + backup/restore) and applies user changes. */
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val uiState: StateFlow<AppSettingsUiState> = repository.settings
        .map { AppSettingsUiState(language = it.language) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            // Seed with the language already applied to the activity so the locale-change effect
            // doesn't spuriously recreate on first composition.
            initialValue = AppSettingsUiState(LocaleManager.applied ?: AppLanguage.SYSTEM),
        )

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onLanguageChange(language: AppLanguage) {
        viewModelScope.launch { repository.update { it.copy(language = language) } }
    }

    fun onExportBackup(target: Uri) {
        viewModelScope.launch {
            val event = backupManager.export(target)
                .fold({ BackupEvent.Exported }, { BackupEvent.Failed(it.message) })
            _events.send(event)
        }
    }

    fun onImportBackup(source: Uri) {
        viewModelScope.launch {
            val event = backupManager.import(source)
                .fold({ BackupEvent.Imported }, { BackupEvent.Failed(it.message) })
            _events.send(event)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class AppSettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
)

/** One-off results of a backup/restore action, surfaced to the screen. */
sealed interface BackupEvent {
    data object Exported : BackupEvent
    data object Imported : BackupEvent
    data class Failed(val message: String?) : BackupEvent
}
