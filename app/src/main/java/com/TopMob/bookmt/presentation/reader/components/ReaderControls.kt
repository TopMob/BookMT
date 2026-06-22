package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.tts.TtsStatus

/** Top app bar shown when reading controls are visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    visible: Boolean,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        TopAppBar(
            title = {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenToc) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.reader_table_of_contents),
                    )
                }
                IconButton(onClick = onOpenNotes) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.reader_notes_bookmarks),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.reader_settings),
                    )
                }
            },
        )
    }
}

/**
 * Bottom control bar: quick-navigation slider with a live percentage readout plus the multi-voice
 * read-aloud transport.
 */
@Composable
fun ReaderBottomBar(
    visible: Boolean,
    progress: Float,
    showSlider: Boolean,
    currentPage: Int,
    pageCount: Int,
    ttsStatus: TtsStatus,
    activeVoiceId: String?,
    onSeek: (Float) -> Unit,
    onAddBookmark: () -> Unit,
    onPlayPauseTts: () -> Unit,
    onStopTts: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Surface(tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (showSlider) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = progress,
                            onValueChange = onSeek,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                } else {
                    // Slider disabled: show only a clean page indicator.
                    Text(
                        text = stringResource(
                            R.string.reader_page_indicator,
                            currentPage,
                            pageCount.coerceAtLeast(1),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onAddBookmark) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = stringResource(R.string.reader_add_bookmark),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeVoiceId != null && ttsStatus == TtsStatus.PLAYING) {
                            Text(
                                text = activeVoiceId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        IconButton(onClick = onPlayPauseTts) {
                            if (ttsStatus == TtsStatus.PLAYING) {
                                Icon(
                                    Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.reader_stop_tts),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.reader_play_tts),
                                )
                            }
                        }
                        if (ttsStatus == TtsStatus.PLAYING || ttsStatus == TtsStatus.PAUSED) {
                            IconButton(onClick = onStopTts) {
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = stringResource(R.string.reader_stop_tts),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
