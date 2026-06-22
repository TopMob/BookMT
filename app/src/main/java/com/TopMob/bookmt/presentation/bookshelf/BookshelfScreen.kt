package com.TopMob.bookmt.presentation.bookshelf

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.BookSortOrder
import com.TopMob.bookmt.presentation.bookshelf.components.BookGridItem
import com.TopMob.bookmt.presentation.bookshelf.components.BookListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onBookClick: (Long) -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
        androidx.compose.runtime.LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookshelf_title)) },
                actions = {
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
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(IMPORT_MIME_TYPES) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bookshelf_import))
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
                            onClick = { onBookClick(book.id) },
                            onLongClick = { viewModel.onDeleteBook(book.id) },
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
                            onClick = { onBookClick(book.id) },
                            onLongClick = { viewModel.onDeleteBook(book.id) },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
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
    "application/pdf",
    "application/octet-stream", // .fb2/.md sometimes report a generic type
)
