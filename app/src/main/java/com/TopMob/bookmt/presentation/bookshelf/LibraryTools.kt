package com.TopMob.bookmt.presentation.bookshelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.domain.usecase.matchesQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFiltersDialog(state: BookshelfUiState, onApply: (BookQuery) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(state.query) }
    var tagSearch by remember { mutableStateOf("") }
    val effective = draft.copy(searchQuery = state.query.searchQuery)
    val count by produceState<Int?>(null, state.allBooks, effective) {
        value = null
        value = withContext(Dispatchers.Default) { state.allBooks.count { it.matchesQuery(effective) } }
    }
    val tags = remember(state.availableTags, tagSearch) { state.availableTags.filter { it != "Favorites" && it.contains(tagSearch, true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_filters)) }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.library_status), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryStatus.entries.forEach { status -> FilterChip(status == draft.status,
                        { draft = draft.copy(status = status, onlyInProgress = false) }, label = { Text(statusLabel(status)) }) }
                }
                FilterChip("Favorites" in draft.tags, { draft = draft.copy(tags = if ("Favorites" in draft.tags) draft.tags - "Favorites" else draft.tags + "Favorites") },
                    label = { Text(stringResource(R.string.favorites)) })
                Text(stringResource(R.string.book_info_format), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(null) + BookFormat.entries.filter { it != BookFormat.UNKNOWN }).forEach { format ->
                        FilterChip(format == draft.format, { draft = draft.copy(format = format) }, label = { Text(format?.name ?: stringResource(R.string.library_all)) })
                    }
                }
                Text(stringResource(R.string.library_collections), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.library_collection_all), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(tagSearch, { tagSearch = it }, label = { Text(stringResource(R.string.action_search)) }, singleLine = true)
            }
            items(tags, key = { it }) { tag ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(tag in draft.tags, role = Role.Checkbox) { draft = draft.copy(tags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(tag in draft.tags, null)
                    Text(tag, Modifier.weight(1f))
                }
            }
            item {
                TextButton(onClick = { draft = draft.copy(status = LibraryStatus.ALL, tags = emptySet(), format = null, onlyInProgress = false) }) {
                    Text(stringResource(R.string.library_reset_filters)) }
            }
        }
    }, confirmButton = { TextButton(enabled = count != null, onClick = { onApply(effective) }) {
        Text(if (count != null) stringResource(R.string.library_show_count, count!!) else stringResource(R.string.comfort_loading)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
fun LibrarySortDialog(query: BookQuery, onSelect: (BookSortOrder, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.action_sort)) }, text = {
        Column {
            BookSortOrder.entries.forEach { order ->
                ChoiceOption(stringResource(sortLabel(order)), query.sortOrder == order) {
                    onSelect(order, if (order == query.sortOrder) query.ascending else order in setOf(BookSortOrder.TITLE, BookSortOrder.AUTHOR, BookSortOrder.PROGRESS))
                }
            }
            HorizontalDivider()
            TextButton(onClick = { onSelect(query.sortOrder, !query.ascending) }) {
                Text(stringResource(if (query.ascending) R.string.library_ascending else R.string.library_descending)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } })
}

@Composable
fun LibraryViewDialog(mode: BookshelfViewMode, onSelect: (BookshelfViewMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_view)) }, text = {
        Column { BookshelfViewMode.entries.forEach { value -> ChoiceOption(
            stringResource(if (value == BookshelfViewMode.LIST) R.string.action_list_view else R.string.action_grid_view), value == mode) { onSelect(value) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } })
}

@Composable
private fun ChoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f))
    }
}

@Composable
fun statusLabel(status: LibraryStatus) = stringResource(when (status) {
    LibraryStatus.ALL -> R.string.library_all
    LibraryStatus.UNREAD -> R.string.library_unread
    LibraryStatus.READING -> R.string.library_reading
    LibraryStatus.FINISHED -> R.string.library_finished
})
fun sortLabel(order: BookSortOrder) = when (order) {
    BookSortOrder.RECENTLY_ADDED -> R.string.sort_recently_added
    BookSortOrder.RECENTLY_READ -> R.string.sort_recently_read
    BookSortOrder.TITLE -> R.string.sort_title
    BookSortOrder.AUTHOR -> R.string.sort_author
    BookSortOrder.PROGRESS -> R.string.sort_progress
}
