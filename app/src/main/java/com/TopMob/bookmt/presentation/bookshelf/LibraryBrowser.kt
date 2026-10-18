package com.TopMob.bookmt.presentation.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.data.importing.ImportProgress
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.bookshelf.components.*
import com.TopMob.bookmt.presentation.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LibraryCallbacks(
    val open: (Book) -> Unit = {}, val context: (Book) -> Unit = {}, val add: () -> Unit = {},
    val settings: () -> Unit = {}, val marks: () -> Unit = {}, val search: (String) -> Unit = {},
    val filters: (BookQuery) -> Unit = {}, val reset: () -> Unit = {},
    val sort: (BookSortOrder, Boolean) -> Unit = { _, _ -> }, val view: (BookshelfViewMode) -> Unit = {},
    val select: () -> Unit = {},
)

/** UI projection; ownership/import/data work stays in the ViewModel. Also usable with deterministic test data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBrowser(state: BookshelfUiState, callbacks: LibraryCallbacks, snackbar: SnackbarHostState,
    progress: ImportProgress? = null, selectedIds: Set<Long> = emptySet(), selectionMode: Boolean = selectedIds.isNotEmpty(), selectionBar: @Composable () -> Unit = {}) {
    var searching by rememberSaveable { mutableStateOf(state.query.searchQuery.isNotEmpty()) }
    var draft by rememberSaveable { mutableStateOf(state.query.searchQuery) }
    var more by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val grid = rememberLazyGridState()
    var oldView by rememberSaveable { mutableStateOf(state.viewMode.name) }
    var sortKey by rememberSaveable { mutableStateOf("${state.query.sortOrder}/${state.query.ascending}") }
    var oldListIndex by rememberSaveable { mutableIntStateOf(0) }
    var oldListOffset by rememberSaveable { mutableIntStateOf(0) }
    var oldGridIndex by rememberSaveable { mutableIntStateOf(0) }
    var oldGridOffset by rememberSaveable { mutableIntStateOf(0) }
    var wasSearching by rememberSaveable { mutableStateOf(searching) }
    var restoreAfterSearch by rememberSaveable { mutableStateOf(false) }
    val selected = selectionMode
    val favoritesLabel = stringResource(R.string.favorites)
    val latestSearch by rememberUpdatedState(callbacks.search)
    LaunchedEffect(draft, state.query.searchQuery) {
        if (draft != state.query.searchQuery) { delay(180); latestSearch(draft) }
    }
    LaunchedEffect(searching, state.query.searchQuery, state.isLoading) {
        if (searching && !wasSearching) {
            oldListIndex = list.firstVisibleItemIndex; oldListOffset = list.firstVisibleItemScrollOffset
            oldGridIndex = grid.firstVisibleItemIndex; oldGridOffset = grid.firstVisibleItemScrollOffset
        }
        if (!searching && wasSearching) restoreAfterSearch = true
        wasSearching = searching
        if (restoreAfterSearch && state.query.searchQuery.isEmpty() && !state.isLoading) {
            list.scrollToItem(oldListIndex, oldListOffset); grid.scrollToItem(oldGridIndex, oldGridOffset)
            restoreAfterSearch = false
        }
    }
    LaunchedEffect(state.viewMode, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        if (oldView != state.viewMode.name) {
            if (state.viewMode == BookshelfViewMode.GRID) grid.scrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            else list.scrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
            oldView = state.viewMode.name
        }
    }
    LaunchedEffect(state.query.sortOrder, state.query.ascending, state.isLoading) {
        if (!state.isLoading) {
            val next = "${state.query.sortOrder}/${state.query.ascending}"
            if (sortKey != next) { list.scrollToItem(0); grid.scrollToItem(0); sortKey = next }
        }
    }
    fun closeSearch() { keyboard?.hide(); draft = ""; callbacks.search(""); searching = false }
    BackHandler(searching && !selected && !drawer.isOpen) { closeSearch() }
    val density = LocalDensity.current
    var fabHeight by remember { mutableIntStateOf(0) }
    val bottom = with(density) { fabHeight.toDp() } + 32.dp
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = false, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
            HorizontalDivider()
            NavigationDrawerItem(label = { Text(stringResource(R.string.bookshelf_title)) }, icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                selected = true, onClick = { scope.launch { drawer.close() } })
            NavigationDrawerItem(label = { Text(stringResource(R.string.bookmarks)) }, icon = { Icon(Icons.Filled.Bookmark, null) },
                selected = false, onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); callbacks.marks() } })
            NavigationDrawerItem(label = { Text(stringResource(R.string.settings_title)) }, icon = { Icon(Icons.Filled.Settings, null) },
                selected = false, onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); callbacks.settings() } })
        }
    }) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            if (selected) selectionBar()
            else if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
                TopAppBar(title = {
                    OutlinedTextField(draft, { draft = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.bookshelf_search_hint)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { callbacks.search(draft); keyboard?.hide() }),
                        trailingIcon = { if (draft.isNotEmpty()) IconButton(onClick = { draft = ""; callbacks.search("") }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.library_search_clear)) } })
                }, navigationIcon = { IconButton(onClick = ::closeSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } })
            } else TopAppBar(title = { Text(stringResource(R.string.bookshelf_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, stringResource(R.string.action_menu)) } },
                actions = {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.action_search)) }
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(more, { more = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.library_view)) }, onClick = { more = false; view = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.book_menu_select)) }, enabled = state.books.isNotEmpty(), onClick = { more = false; callbacks.select() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.bookshelf_import)) }, onClick = { more = false; callbacks.add() })
                        }
                    }
                })
        }, floatingActionButton = {
            if (!selected && state.allBooks.isNotEmpty()) FloatingActionButton(onClick = callbacks.add,
                modifier = Modifier.onSizeChanged { fabHeight = it.height }) {
                Icon(Icons.Filled.Add, stringResource(R.string.bookshelf_import))
            }
        }) { padding ->
            val header: @Composable () -> Unit = {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (!searching && !selected) state.continueReadingBook?.let { ContinueReadingCard(it) { callbacks.open(it) } }
                    if (!selected && state.allBooks.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { filters = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.FilterList, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.library_filters)) }
                            TextButton(onClick = { sort = true }, modifier = Modifier.weight(1f)) { Text(stringResource(sortLabel(state.query.sortOrder))) }
                        }
                        if (state.query.tags.isNotEmpty() || state.query.status != LibraryStatus.ALL || state.query.format != null) {
                            TextButton(onClick = { draft = ""; callbacks.reset() }) {
                                Text(listOfNotNull(statusLabel(state.query.status), state.query.format?.name,
                                    state.query.tags.takeIf { it.isNotEmpty() }?.joinToString { if (it == "Favorites") favoritesLabel else it })
                                    .joinToString(" · "))
                            }
                        }
                    }
                    progress?.let { Text(stringResource(R.string.library_import_progress, it.completed, it.total), Modifier.padding(vertical = 8.dp)) }
                    if (draft != state.query.searchQuery) Text(stringResource(R.string.library_searching), Modifier.padding(vertical = 8.dp))
                }
            }
            val empty: @Composable () -> Unit = {
                ComfortEmptyState(stringResource(if (state.allBooks.isEmpty()) R.string.comfort_library_title else R.string.comfort_no_matches),
                    stringResource(if (state.allBooks.isEmpty()) R.string.comfort_library_hint else R.string.comfort_no_matches_hint),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    actionLabel = stringResource(if (state.allBooks.isEmpty()) R.string.bookshelf_import else R.string.library_reset_filters),
                    onAction = { if (state.allBooks.isEmpty()) callbacks.add() else { draft = ""; callbacks.reset() } })
            }
            if (state.isLoading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ComfortLoadingIndicator() }
            else if (state.viewMode == BookshelfViewMode.GRID) LazyVerticalGrid(
                columns = GridCells.Adaptive(if (density.fontScale >= 1.5f) 192.dp else 144.dp), state = grid,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottom)) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                if (state.books.isEmpty() && draft == state.query.searchQuery) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
                items(state.books, key = { it.id }) { book -> BookGridItem(book, { callbacks.open(book) }, { callbacks.context(book) },
                    selected = book.id in selectedIds, selectionMode = selected) }
            } else LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom)) {
                item(key = "header") { header() }
                if (state.books.isEmpty() && draft == state.query.searchQuery) item { empty() }
                items(state.books, key = { it.id }) { book -> BookListItem(book, { callbacks.open(book) }, { callbacks.context(book) },
                    selected = book.id in selectedIds, selectionMode = selected, modifier = Modifier.padding(vertical = 6.dp)) }
            }
        }
    }
    if (filters) LibraryFiltersDialog(state, { callbacks.filters(it); filters = false }, { filters = false })
    if (sort) LibrarySortDialog(state.query, { order, asc -> callbacks.sort(order, asc); sort = false }, { sort = false })
    if (view) LibraryViewDialog(state.viewMode, { callbacks.view(it); view = false }, { view = false })
}

@Composable
fun ContinueReadingCard(book: Book, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book, Modifier.size(48.dp, 72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge)
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.library_progress_label, book.progressPercent), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.MenuBook, null)
        }
    }
}
