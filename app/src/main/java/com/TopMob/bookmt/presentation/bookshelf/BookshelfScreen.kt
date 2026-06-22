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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
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
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Transient UI state held here so it survives list recomposition.
    var contextMenuBook by remember { mutableStateOf<Book?>(null) }
    var pendingDeleteBook by remember { mutableStateOf<Book?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searching by remember { mutableStateOf(false) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    val selectionMode = selectedIds.isNotEmpty()
    val selectedBooks = state.books.filter { it.id in selectedIds }

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    // Hardware back exits selection/search before leaving the screen.
    BackHandler(enabled = selectionMode) { clearSelection() }
    BackHandler(enabled = searching && !selectionMode) {
        searching = false
        viewModel.onSearchChange("")
    }

    // System file picker. OpenDocument grants a persistable URI so the book reopens across launches.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val resolver = context.contentResolver
            val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment.orEmpty()
            viewModel.onImportBook(uri.toString(), displayName, resolver.getType(uri))
        }
    }

    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                selectionMode -> SelectionTopBar(
                    count = selectedIds.size,
                    onClose = { clearSelection() },
                    onMarkRead = {
                        viewModel.onSetReadStateForBooks(selectedBooks, read = true)
                        clearSelection()
                    },
                    onMarkUnread = {
                        viewModel.onSetReadStateForBooks(selectedBooks, read = false)
                        clearSelection()
                    },
                    onAddTag = { showTagDialog = true },
                    onDelete = { showBatchDelete = true },
                )

                searching -> SearchTopBar(
                    query = state.query.searchQuery,
                    onQueryChange = viewModel::onSearchChange,
                    onClose = {
                        searching = false
                        viewModel.onSearchChange("")
                    },
                )

                else -> TopAppBar(
                    title = { Text(stringResource(R.string.bookshelf_title)) },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.action_search),
                            )
                        }
                        SortMenu(onSortSelected = viewModel::onSortOrderChange)
                        IconButton(onClick = viewModel::onToggleViewMode) {
                            if (state.viewMode == BookshelfViewMode.GRID) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(R.string.action_list_view),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.GridView,
                                    contentDescription = stringResource(R.string.action_grid_view),
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { importLauncher.launch(IMPORT_MIME_TYPES) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bookshelf_import))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isEmpty -> Text(
                    text = stringResource(R.string.bookshelf_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                state.viewMode == BookshelfViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = state.books, key = { it.id }) { book ->
                        BookGridItem(
                            book = book,
                            selected = book.id in selectedIds,
                            onClick = {
                                if (selectionMode) toggleSelection(book.id) else onBookClick(book.id)
                            },
                            onLongClick = {
                                if (selectionMode) toggleSelection(book.id) else contextMenuBook = book
                            },
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = state.books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            selected = book.id in selectedIds,
                            onClick = {
                                if (selectionMode) toggleSelection(book.id) else onBookClick(book.id)
                            },
                            onLongClick = {
                                if (selectionMode) toggleSelection(book.id) else contextMenuBook = book
                            },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    // Long-press context menu (single book).
    contextMenuBook?.let { book ->
        BookContextSheet(
            book = book,
            onOpenDetails = {
                contextMenuBook = null
                onOpenDetails(book.id)
            },
            onToggleRead = {
                viewModel.onSetReadState(book, read = book.progress < 1f)
                contextMenuBook = null
            },
            onStartSelection = {
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
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.bookshelf_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.book_menu_delete))
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.book_menu_mark_read)) },
                    onClick = { menuExpanded = false; onMarkRead() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.book_menu_mark_unread)) },
                    onClick = { menuExpanded = false; onMarkUnread() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.book_menu_add_tag)) },
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

@Composable
private fun SortMenu(onSortSelected: (BookSortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SORT_OPTIONS.forEach { (order, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = {
                    onSortSelected(order)
                    expanded = false
                },
            )
        }
    }
}

private val SORT_OPTIONS = listOf(
    BookSortOrder.RECENTLY_ADDED to R.string.sort_recently_added,
    BookSortOrder.RECENTLY_READ to R.string.sort_recently_read,
    BookSortOrder.TITLE to R.string.sort_title,
    BookSortOrder.AUTHOR to R.string.sort_author,
    BookSortOrder.PROGRESS to R.string.sort_progress,
)

private val IMPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "text/markdown",
    "application/x-fictionbook+xml",
    "application/epub+zip",
    "application/pdf",
    "application/octet-stream", // .fb2/.md/.epub sometimes report a generic type
)
