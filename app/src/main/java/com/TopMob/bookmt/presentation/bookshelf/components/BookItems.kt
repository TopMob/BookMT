package com.TopMob.bookmt.presentation.bookshelf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Book

@Composable
fun BookGridItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, selectionMode: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp)) {
        Box {
            BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Surface(Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 12.dp)) {
                if (selectionMode) Checkbox(selected, { onClick() })
                else IconButton(onClick = onLongClick) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more) + ": " + book.title) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        BookStatus(book)
    }
}

@Composable
fun BookListItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, selectionMode: Boolean = false) {
    Card(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCover(book, Modifier.size(56.dp, 84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                BookStatus(book)
            }
            if (selectionMode) Checkbox(selected, { onClick() })
            else IconButton(onClick = onLongClick) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more) + ": " + book.title) }
        }
    }
}

@Composable
private fun BookStatus(book: Book) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if ("Favorites" in book.tags) Icon(Icons.Filled.Favorite, stringResource(R.string.favorites), Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (book.isFinished) stringResource(R.string.library_finished) else stringResource(R.string.library_progress_label, book.progressPercent),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
