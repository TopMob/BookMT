package com.TopMob.bookmt.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.ReaderSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsDialog(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.HOME.name) }
    val page = SettingsPage.valueOf(pageName)
    val holder = rememberSaveableStateHolder()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(page != SettingsPage.HOME) { pageName = SettingsPage.HOME.name }
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(page.title)) }, navigationIcon = {
                    IconButton(onClick = { if (page == SettingsPage.HOME) onDismiss() else pageName = SettingsPage.HOME.name }) {
                        Icon(if (page == SettingsPage.HOME) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(if (page == SettingsPage.HOME) R.string.action_close else R.string.action_back))
                    }
                })
            }) { padding ->
                holder.SaveableStateProvider(pageName) {
                    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                        when (page) {
                            SettingsPage.HOME -> SettingsHome({ pageName = it.name }, readerOnly = true)
                            SettingsPage.APPEARANCE -> AppearancePreferences(settings, update)
                            SettingsPage.READING -> ReadingPreferences(settings, update)
                            SettingsPage.SPEECH -> Column(Modifier.padding(horizontal = 20.dp)) { TtsPreferences(settings, update) }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
