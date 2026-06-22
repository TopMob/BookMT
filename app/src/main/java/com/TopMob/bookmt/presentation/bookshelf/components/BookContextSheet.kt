package com.TopMob.bookmt.presentation.bookshelf.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.Book

/**
 * Long-press context menu for a book. Replaces the previous "long-press instantly deletes" behavior
 * with explicit, reversible actions. Deletion is routed through a confirmation dialog by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookContextSheet(
    book: Book,
    onOpenDetails: () -> Unit,
    onToggleRead: () -> Unit,
    onStartSelection: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isRead = book.progress >= 1f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            MenuRow(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.book_menu_info),
                onClick = onOpenDetails,
            )
            MenuRow(
                icon = Icons.Outlined.CheckCircle,
                text = stringResource(
                    if (isRead) R.string.book_menu_mark_unread else R.string.book_menu_mark_read,
                ),
                onClick = onToggleRead,
            )
            MenuRow(
                icon = Icons.Outlined.Checklist,
                text = stringResource(R.string.book_menu_select),
                onClick = onStartSelection,
            )
            MenuRow(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.book_menu_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onRequestDelete,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** Confirmation shown before a destructive delete. */
@Composable
fun DeleteBookDialog(
    book: Book,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_book_title)) },
        text = { Text(stringResource(R.string.delete_book_message, book.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Read-only book details. */
@Composable
fun BookInfoDialog(
    book: Book,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = book.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                InfoLine(
                    label = stringResource(R.string.book_info_author),
                    value = book.author ?: stringResource(R.string.book_info_unknown_author),
                )
                Spacer(Modifier.height(8.dp))
                InfoLine(
                    label = stringResource(R.string.book_info_format),
                    value = book.format.name,
                )
                Spacer(Modifier.height(8.dp))
                InfoLine(
                    label = stringResource(R.string.book_info_progress),
                    value = stringResource(R.string.book_info_progress_value, book.progressPercent),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
