package com.TopMob.bookmt.presentation.reader.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.presentation.reader.*
import com.TopMob.bookmt.presentation.settings.ReadingPreferences

data class ReaderSelection(val page: ReaderPage, val range: TextRange)

@Composable
fun ReaderToolDialogs(state: ReaderUiState, viewModel: ReaderViewModel, onGoToPage: (Int) -> Unit) {
    when (state.overlay) {
        ReaderOverlay.SETTINGS -> AlertDialog(
            onDismissRequest = viewModel::onDismissOverlay,
            title = { Text(stringResource(R.string.settings_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                ReadingPreferences(state.settings, viewModel::onUpdateSettings)
            } },
            confirmButton = { TextButton(onClick = viewModel::onDismissOverlay) { Text(stringResource(R.string.action_close)) } },
        )
        ReaderOverlay.SEARCH -> AlertDialog(
            onDismissRequest = viewModel::onDismissOverlay,
            title = { Text(stringResource(R.string.reader_search_book)) },
            text = { Column {
                OutlinedTextField(state.searchQuery, viewModel::onSearchChange, singleLine = true,
                    label = { Text(stringResource(R.string.action_search)) })
                if (state.searchBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (state.searchLimited) R.string.reader_search_limited else R.string.reader_search_count, state.searchResults.size))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.searchResults, key = { it.offset }) { match ->
                        Column(Modifier.fillMaxWidth().clickable {
                            viewModel.jumpToOffset(match.offset)
                            viewModel.onDismissOverlay()
                        }.padding(vertical = 12.dp)) {
                            match.chapter?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            Text(match.excerpt, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider()
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = viewModel::onDismissOverlay) { Text(stringResource(R.string.action_close)) } },
        )
        ReaderOverlay.GO_TO -> {
            var page by remember { mutableStateOf(state.currentPage.toString()) }
            val target = page.toIntOrNull()
            AlertDialog(onDismissRequest = viewModel::onDismissOverlay,
                title = { Text(stringResource(R.string.reader_go_to_page)) },
                text = { OutlinedTextField(page, { page = it.filter(Char::isDigit).take(8) },
                    label = { Text(if (state.paginationReady) "1 – ${state.totalPages}" else stringResource(R.string.reader_measuring_pages)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
                confirmButton = { TextButton(enabled = state.paginationReady && target != null && target in 1..state.totalPages, onClick = {
                    target?.let { onGoToPage(it - 1) }; viewModel.onDismissOverlay()
                }) { Text(stringResource(R.string.reader_go)) } },
                dismissButton = { TextButton(onClick = viewModel::onDismissOverlay) { Text(stringResource(R.string.action_cancel)) } })
        }
        else -> Unit
    }
}

@Suppress("DEPRECATION")
@Composable
fun SelectionActionsDialog(selection: ReaderSelection, onDismiss: () -> Unit,
    onHighlight: (Int, Int, String, Int?) -> Unit, onNote: (Int, Int, String, String) -> Unit) {
    var value by remember(selection) { mutableStateOf(TextFieldValue(selection.page.text, selection.range)) }
    var note by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0xFFFFD54F.toInt()) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_selection)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.reader_selection_hint), style = MaterialTheme.typography.bodySmall)
            BasicTextField(value, { value = it.copy(text = selection.page.text) }, readOnly = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(vertical = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0xFFFFD54F, 0xFF81C784, 0xFF64B5F6, 0xFFF48FB1).forEachIndexed { index, argb ->
                    FilterChip(color == argb.toInt(), { color = argb.toInt() },
                        label = { Text((index + 1).toString()) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(argb), selectedContainerColor = Color(argb)))
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.reader_note)) }, maxLines = 4)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(enabled = selected.isNotBlank(), onClick = {
                    clipboard.setText(AnnotatedString(selected)); onDismiss()
                }) { Text(stringResource(R.string.reader_copy)) }
                TextButton(enabled = selected.isNotBlank(), onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, selected), null))
                }) { Text(stringResource(R.string.reader_share)) }
            }
        } },
        confirmButton = { TextButton(enabled = selected.isNotBlank(), onClick = {
            val absoluteStart = selection.page.startOffset + start
            val absoluteEnd = selection.page.startOffset + end
            if (note.isBlank()) onHighlight(absoluteStart, absoluteEnd, selected, color)
            else onNote(absoluteStart, absoluteEnd, selected, note.trim())
            onDismiss()
        }) { Text(stringResource(if (note.isBlank()) R.string.reader_highlight else R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
fun EditAnnotationDialog(bookmark: Bookmark, onSave: (Bookmark) -> Unit, onDismiss: () -> Unit) {
    var note by remember(bookmark) { mutableStateOf(bookmark.note.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.reader_note)) },
        text = { Column { Text(bookmark.textSnippet, maxLines = 4)
            OutlinedTextField(note, { note = it }, minLines = 3, maxLines = 8) } },
        confirmButton = { TextButton(onClick = { onSave(bookmark.copy(note = note.trim().ifBlank { null })); onDismiss() }) {
            Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
