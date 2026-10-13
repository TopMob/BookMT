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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    bookmarks: BookmarkRepository,
) : ViewModel() {
    val entries = combine(books.observeBooks(), bookmarks.observeAllBookmarks()) { library, notes ->
        val titles = library.associate { it.id to it.title }
        notes.mapNotNull { note -> titles[note.bookId]?.let { LibraryBookmark(note, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalBookmarksScreen(
    onBack: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    viewModel: GlobalBookmarksViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.bookmarks)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { padding ->
        if (entries.isEmpty()) {
            Text(stringResource(R.string.notes_empty), Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.bookmark.id }) { entry ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(entry.bookmark) }.padding(16.dp)) {
                        Text(entry.bookTitle, style = MaterialTheme.typography.titleMedium)
                        entry.bookmark.chapterTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        Text(entry.bookmark.note ?: entry.bookmark.textSnippet, maxLines = 3)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
