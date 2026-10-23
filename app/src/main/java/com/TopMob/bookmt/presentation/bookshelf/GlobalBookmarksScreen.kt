package com.TopMob.bookmt.presentation.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import com.TopMob.bookmt.domain.model.BookmarkType
import com.TopMob.bookmt.presentation.reader.components.EditAnnotationDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.repository.BookRepository
import com.TopMob.bookmt.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryBookmark(val bookmark: Bookmark, val bookTitle: String)

@HiltViewModel
class GlobalBookmarksViewModel @Inject constructor(
    books: BookRepository,
    private val bookmarks: BookmarkRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val entries = combine(books.observeBooks(), bookmarks.observeAllBookmarks()) { library, notes ->
        val titles = library.associate { it.id to it.title }
        notes.mapNotNull { note -> titles[note.bookId]?.let { LibraryBookmark(note, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    fun delete(bookmark: Bookmark) = action { bookmarks.deleteBookmark(bookmark.id) }
    fun update(bookmark: Bookmark) = action { bookmarks.updateBookmark(bookmark) }
    private var exportSelection: List<LibraryBookmark> = emptyList()
    fun selectForExport(entries: List<LibraryBookmark>) { exportSelection = entries.toList() }
    fun finishExport(uri: android.net.Uri?) {
        val selected = exportSelection
        exportSelection = emptyList()
        if (uri != null) export(uri, selected)
    }
    private fun export(uri: android.net.Uri, entries: List<LibraryBookmark>) = action {
        check(entries.isNotEmpty()) { "Choose notes to export again" }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                for (notes in entries.groupBy { it.bookmark.bookId }.values) {
                    writer.appendLine(notes.first().bookTitle)
                    writer.appendLine("=".repeat(40))
                    notes.sortedBy { it.bookmark.position }.forEach { entry ->
                        val note = entry.bookmark
                        writer.appendLine(note.chapterTitle.orEmpty())
                        writer.appendLine(note.textSnippet)
                        note.note?.let { writer.appendLine(it) }
                        writer.appendLine()
                    }
                }
            } ?: error("Cannot write notes file")
        }
        messages.send(context.getString(R.string.offline_notes_exported))
    }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.send(e.message ?: "Cannot update notes") }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalBookmarksScreen(
    onBack: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    viewModel: GlobalBookmarksViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var deleting by remember { mutableStateOf<Bookmark?>(null) }
    val visible = remember(entries, query, typeName) {
        val needle = query.trim()
        entries.filter {
            (typeName == null || it.bookmark.type.name == typeName) &&
                (needle.isBlank() || listOf(it.bookTitle, it.bookmark.chapterTitle.orEmpty(),
                    it.bookmark.note.orEmpty(), it.bookmark.textSnippet).any { text -> text.contains(needle, ignoreCase = true) })
        }
    }
    val exporter = rememberLauncherForActivityResult(com.TopMob.bookmt.presentation.util.LocalCreateDocument("text/plain")) { uri ->
        viewModel.finishExport(uri)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.bookmarks)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        }, actions = {
            IconButton(enabled = visible.isNotEmpty(), onClick = {
                viewModel.selectForExport(visible)
                exporter.launch("bookmt-notes.txt")
            }) { Icon(Icons.Filled.SaveAlt, stringResource(R.string.offline_export_notes)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text(stringResource(R.string.offline_notes_search)) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(typeName == null, { typeName = null }, label = { Text(stringResource(R.string.library_all)) })
                BookmarkType.entries.forEach { type ->
                    FilterChip(typeName == type.name, { typeName = type.name }, label = {
                        Text(stringResource(when (type) {
                            BookmarkType.BOOKMARK -> R.string.bookmarks
                            BookmarkType.HIGHLIGHT -> R.string.reader_highlight
                            BookmarkType.NOTE -> R.string.reader_note
                        }))
                    })
                }
            }
            Text(stringResource(R.string.offline_notes_count, visible.size), Modifier.padding(horizontal = 16.dp))
            if (visible.isEmpty()) Text(stringResource(R.string.notes_empty), Modifier.padding(24.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.bookmark.id }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f).clickable { onOpen(entry.bookmark) }) {
                            Text(entry.bookTitle, style = MaterialTheme.typography.titleMedium)
                            entry.bookmark.chapterTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                            Text(entry.bookmark.textSnippet, maxLines = 3)
                            entry.bookmark.note?.let { Text(it, maxLines = 3, color = MaterialTheme.colorScheme.primary) }
                        }
                        IconButton(onClick = { editing = entry.bookmark }) { Icon(Icons.Filled.Edit, stringResource(R.string.reader_note)) }
                        IconButton(onClick = { deleting = entry.bookmark }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    editing?.let { EditAnnotationDialog(it, viewModel::update) { editing = null } }
    deleting?.let { bookmark ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.offline_delete_note)) },
            text = { Text(bookmark.note ?: bookmark.textSnippet, maxLines = 5) },
            confirmButton = { TextButton(onClick = { viewModel.delete(bookmark); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
