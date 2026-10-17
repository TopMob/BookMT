package com.TopMob.bookmt.presentation.bookshelf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.data.importing.ImportProgress
import com.TopMob.bookmt.domain.model.BookFormat
import com.TopMob.bookmt.domain.model.LibraryStatus

@Composable
fun LibraryToolsDialog(state: BookshelfUiState, model: BookshelfViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_tools)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.library_status))
            LibraryChips(LibraryStatus.entries, state.query.status, { statusLabel(it) }, model::onStatusFilter)
            Text(stringResource(R.string.book_info_format))
            LibraryChips(listOf(null) + BookFormat.entries.filter { it != BookFormat.UNKNOWN }, state.query.format,
                { it?.name ?: stringResource(R.string.library_all) }, model::onFormatFilter)
            Text(stringResource(R.string.library_collections))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.query.tags.isEmpty(), { model.onTagFilterChange(emptySet()) }, label = { Text(stringResource(R.string.library_all)) })
                state.availableTags.forEach { tag ->
                    FilterChip(tag in state.query.tags, {
                        model.onTagFilterChange(if (tag in state.query.tags) state.query.tags - tag else state.query.tags + tag)
                    }, label = { Text(if (tag == "Favorites") stringResource(R.string.favorites) else tag) })
                }
            }
            Text(stringResource(R.string.library_collections_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = model::onReverseSort) {
                Text(stringResource(if (state.query.ascending) R.string.library_ascending else R.string.library_descending))
            }
            TextButton(enabled = state.allBooks.isNotEmpty(), onClick = { model.onPrepareOffline(); onDismiss() }) {
                Text(stringResource(R.string.offline_prepare_library))
            }
            Text(stringResource(R.string.offline_prepare_hint), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.library_statistics), style = MaterialTheme.typography.titleMedium)
            val books = state.allBooks
            val finishedCount = androidx.compose.runtime.remember(books) { books.count { it.isFinished } }
            val readingCount = androidx.compose.runtime.remember(books) { books.count { it.progress > 0f && !it.isFinished } }
            val totalTimeMs = androidx.compose.runtime.remember(books) { books.sumOf { it.totalReadingTimeMs } }
            val totalSizeBytes = androidx.compose.runtime.remember(books) { books.sumOf { it.fileSizeBytes } }
            val topBooks = androidx.compose.runtime.remember(books) {
                books.filter { it.totalReadingTimeMs > 0 }.sortedByDescending { it.totalReadingTimeMs }.take(5)
            }
            Text(stringResource(R.string.library_stats_books, books.size, finishedCount, readingCount))
            val minutes = totalTimeMs / 60000
            Text(stringResource(R.string.library_stats_time, minutes / 60, minutes % 60))
            Text(stringResource(R.string.library_stats_size, totalSizeBytes / (1024 * 1024)))
            topBooks.forEach {
                Text("${it.title} · ${it.totalReadingTimeMs / 60000} ${stringResource(R.string.library_minutes)}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = { TextButton(onClick = model::onResetFilters) { Text(stringResource(R.string.library_reset_filters)) } })
}

@Composable
fun LibraryFilterSummary(state: BookshelfUiState, progress: ImportProgress?, onReset: () -> Unit) {
    if (progress != null) {
        LinearProgressIndicator(progress = { if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.library_import_progress, progress.completed, progress.total), Modifier.padding(horizontal = 12.dp))
    }
    if (state.query.tags.isNotEmpty() || state.query.status != LibraryStatus.ALL || state.query.format != null) {
        TextButton(onClick = onReset) {
            Text("${statusLabel(state.query.status)} · ${state.query.format?.name.orEmpty()} ${state.query.tags.joinToString()} · ${state.books.size} — ${stringResource(R.string.library_reset_filters)}")
        }
    }
}

@Composable
private fun statusLabel(status: LibraryStatus) = stringResource(when (status) {
    LibraryStatus.ALL -> R.string.library_all
    LibraryStatus.UNREAD -> R.string.library_unread
    LibraryStatus.READING -> R.string.library_reading
    LibraryStatus.FINISHED -> R.string.library_finished
})

@Composable
private fun <T> LibraryChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(it == selected, { onSelect(it) }, label = { Text(label(it)) }) }
    }
}
