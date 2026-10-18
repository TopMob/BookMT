package com.TopMob.bookmt.presentation.bookshelf

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Book
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.presentation.bookshelf.components.BookContextSheet
import com.TopMob.bookmt.presentation.bookshelf.components.BookGridItem
import com.TopMob.bookmt.presentation.bookshelf.components.BookListItem
import com.TopMob.bookmt.presentation.bookshelf.components.DeleteBookDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onBookClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenBookmarks: () -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var contextMenuBook by remember { mutableStateOf<Book?>(null) }
    var pendingDeleteBook by remember { mutableStateOf<Book?>(null) }
    var selectedIds by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf<Set<Long>>(emptySet()) }
    var selectionActive by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    val selectedBooks = state.books.filter { it.id in selectedIds }
    fun clearSelection() { selectedIds = emptySet(); selectionActive = false }
    fun toggleSelection(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    LaunchedEffect(state.books, state.isLoading) {
        if (!state.isLoading) selectedIds = selectedIds.intersect(state.books.map { it.id }.toSet())
    }
    BackHandler(selectionActive) { clearSelection() }
    val importLauncher = rememberLauncherForActivityResult(com.TopMob.bookmt.presentation.util.LocalOpenMultipleDocuments()) { uris -> viewModel.onImportDocuments(uris) }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onErrorShown() }
    }
    LibraryBrowser(state, LibraryCallbacks(
        open = { if (selectionActive) toggleSelection(it.id) else onBookClick(it.id) },
        context = { if (selectionActive) toggleSelection(it.id) else contextMenuBook = it },
        add = { importLauncher.launch(IMPORT_MIME_TYPES) }, settings = onOpenSettings, marks = onOpenBookmarks,
        search = viewModel::onSearchChange, filters = viewModel::onApplyFilters, reset = viewModel::onResetFilters,
        sort = viewModel::onSortChange, view = viewModel::onSetViewMode,
        select = { selectionActive = true },
    ), snackbarHostState, progress, selectedIds, selectionActive, selectionBar = {
        SelectionTopBar(selectedIds.size, ::clearSelection,
            onMarkRead = { viewModel.onSetReadStateForBooks(selectedBooks, true); clearSelection() },
            onMarkUnread = { viewModel.onSetReadStateForBooks(selectedBooks, false); clearSelection() },
            onAddTag = { showTagDialog = true }, onDelete = { showBatchDelete = true },
            onSelectAll = { selectedIds = state.books.map { it.id }.toSet() }, available = state.books.size)
    })

    // Long-press context menu (single book).
    contextMenuBook?.let { book ->
        BookContextSheet(
            book = book,
            onRead = { contextMenuBook = null; onBookClick(book.id) },
            onFavorite = { viewModel.onToggleFavorite(book); contextMenuBook = null },
            onOpenDetails = {
                contextMenuBook = null
                onOpenDetails(book.id)
            },
            onToggleRead = {
                viewModel.onSetReadState(book, read = !book.isFinished)
                contextMenuBook = null
            },
            onStartSelection = {
                selectionActive = true
                selectedIds = setOf(book.id)
                contextMenuBook = null
            },
            onRequestDelete = {
                pendingDeleteBook = book
                contextMenuBook = null
            },
            onDismiss = { contextMenuBook = null },
        )
    }

    pendingDeleteBook?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = {
                viewModel.onDeleteBook(book.id)
                pendingDeleteBook = null
            },
            onDismiss = { pendingDeleteBook = null },
        )
    }

    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text(stringResource(R.string.delete_books_title)) },
            text = { Text(stringResource(R.string.delete_books_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteBooks(selectedIds)
                    showBatchDelete = false
                    clearSelection()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showTagDialog) {
        AddTagDialog(
            onConfirm = { tag ->
                viewModel.onAddTagToBooks(selectedBooks, tag)
                showTagDialog = false
                clearSelection()
            },
            onDismiss = { showTagDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onAddTag: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit, available: Int,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        title = { Text(stringResource(R.string.selection_count, count)) },
        actions = {
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.book_menu_delete))
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.library_select_all, available)) },
                    onClick = { menuExpanded = false; onSelectAll() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.book_menu_mark_read)) }, enabled = count > 0,
                    onClick = { menuExpanded = false; onMarkRead() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_keep_position_unread)) }, enabled = count > 0,
                    onClick = { menuExpanded = false; onMarkUnread() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.book_menu_add_tag)) }, enabled = count > 0,
                    onClick = { menuExpanded = false; onAddTag() },
                )
            }
        },
    )
}

@Composable
private fun AddTagDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_menu_add_tag)) },
        text = {
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.add_tag_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tag) }, enabled = tag.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val IMPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "text/markdown",
    "application/x-fictionbook+xml",
    "application/epub+zip",
    "application/pdf",
    "application/octet-stream", // .fb2/.md/.epub sometimes report a generic type
)
