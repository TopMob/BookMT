package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Bookmark
import com.TopMob.bookmt.domain.model.TocEntry

/**
 * Right-anchored slide-in drawer chrome with a dismiss scrim. Used for both the Notes &amp;
 * Bookmarks panel and the Table of Contents so they share consistent behavior.
 */
@Composable
fun ReaderSideDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun NotesBookmarksPanel(
    bookmarks: List<Bookmark>,
    onEntryClick: (Bookmark) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.reader_notes_bookmarks),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.padding(4.dp))
        if (bookmarks.isEmpty()) {
            Text(
                text = "No bookmarks yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = bookmarks, key = { it.id }) { bookmark ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(bookmark) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        bookmark.chapterTitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = bookmark.textSnippet.ifBlank { bookmark.type.name },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        bookmark.note?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(bookmark.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun TableOfContentsPanel(
    entries: List<TocEntry>,
    onEntryClick: (TocEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.reader_table_of_contents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.padding(4.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = entries, key = { it.offset }) { entry ->
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry) }
                        .padding(start = (entry.level * 16).dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
