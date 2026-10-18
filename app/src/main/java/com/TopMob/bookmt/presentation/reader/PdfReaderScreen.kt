package com.TopMob.bookmt.presentation.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.TopMob.bookmt.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(onBack: () -> Unit, model: PdfReaderViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var showBookmarks by remember { mutableStateOf(false) }
    var scale by remember(state.page) { mutableFloatStateOf(1f) }
    var pan by remember(state.page) { mutableStateOf(Offset.Zero) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.resume()
            if (event == Lifecycle.Event.ON_PAUSE) model.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.pause() }
    }
    val label = stringResource(R.string.reader_page_indicator, state.page + 1, state.count.coerceAtLeast(1))
    Scaffold(topBar = { TopAppBar(title = { Text(state.book?.title ?: "PDF", maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        actions = { IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Filled.Bookmark, stringResource(R.string.bookmarks)) } }) },
        bottomBar = { Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp)) {
            var seek by remember { mutableStateOf<Float?>(null) }
            Slider(seek ?: state.page.toFloat(), { seek = it }, enabled = state.count > 1,
                valueRange = 0f..(state.count - 1).coerceAtLeast(1).toFloat(),
                onValueChangeFinished = { seek?.let { model.goTo(it.toInt()) }; seek = null })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = state.page > 0, onClick = { model.goTo(state.page - 1) }) { Text("‹") }
                TextButton(onClick = { scale = 1f; pan = Offset.Zero }) { Text(label) }
                TextButton(enabled = state.page + 1 < state.count, onClick = { model.goTo(state.page + 1) }) { Text("›") }
                IconButton(enabled = state.count > 0, onClick = { model.addBookmark(label) }) { Icon(Icons.Filled.Bookmark, stringResource(R.string.reader_add_bookmark)) }
            }
        } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            state.bitmap?.let { bitmap ->
                Image(bitmap.asImageBitmap(), label, modifier = Modifier.fillMaxSize().pointerInput(state.page) {
                    detectTransformGestures { _, translation, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val maxX = size.width * (scale - 1) / 2f
                        val maxY = size.height * (scale - 1) / 2f
                        pan = Offset((pan.x + translation.x).coerceIn(-maxX, maxX), (pan.y + translation.y).coerceIn(-maxY, maxY))
                    }
                }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y })
            }
            if (state.loading) com.TopMob.bookmt.presentation.components.ComfortLoadingIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        }
    }
    if (showBookmarks) AlertDialog(onDismissRequest = { showBookmarks = false },
        title = { Text(stringResource(R.string.bookmarks)) }, text = {
            LazyColumn {
                items(state.bookmarks, key = { it.id }) { bookmark ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(modifier = Modifier.weight(1f), onClick = { model.goTo(bookmark.position); showBookmarks = false }) { Text(bookmark.textSnippet) }
                        IconButton(onClick = { model.deleteBookmark(bookmark.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text(stringResource(R.string.action_close)) } })
}
