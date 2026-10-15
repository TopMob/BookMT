package com.TopMob.bookmt.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TtsPreferences(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    var license by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.tts_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.tts_description), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!settings.ttsMaleVoice, { update { it.copy(ttsMaleVoice = false) } },
            label = { Text(stringResource(R.string.tts_female)) })
        FilterChip(settings.ttsMaleVoice, { update { it.copy(ttsMaleVoice = true) } },
            label = { Text(stringResource(R.string.tts_male)) })
    }
    Text(stringResource(R.string.tts_speed, settings.ttsSpeed))
    Slider(settings.ttsSpeed, { value -> update { it.copy(ttsSpeed = value) } }, valueRange = 0.75f..1.5f, steps = 14)
    Text(stringResource(R.string.tts_sentence_pause, settings.ttsSentencePauseMs))
    Slider(settings.ttsSentencePauseMs.toFloat(), { value -> update { it.copy(ttsSentencePauseMs = value.toInt()) } }, valueRange = 0f..1000f, steps = 19)
    Text(stringResource(R.string.tts_paragraph_pause, settings.ttsParagraphPauseMs))
    Slider(settings.ttsParagraphPauseMs.toFloat(), { value -> update { it.copy(ttsParagraphPauseMs = value.toInt()) } }, valueRange = 0f..1500f, steps = 29)
    Text(stringResource(R.string.tts_settings_hint), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { license = true }) { Text(stringResource(R.string.tts_license)) }
    if (license) TtsLicenseDialog(onDismiss = { license = false }) {
        update { it.copy(ttsLicenseAccepted = true) }; license = false
    }
}

@Composable
fun TtsLicenseDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("licenses/Supertonic-3.txt").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.tts_license)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tts_license_intro))
                Spacer(Modifier.height(12.dp))
                Text(text ?: stringResource(R.string.tts_preparing), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(enabled = text != null, onClick = onAccept) { Text(stringResource(R.string.tts_accept)) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tts_cancel)) } })
}
