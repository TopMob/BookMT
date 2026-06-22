package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onToggleTheme: () -> Unit,
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
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = stringResource(R.string.settings_theme),
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
    currentPage: Int,
    totalPages: Int,
    ttsStatus: TtsStatus,
    activeVoiceId: String?,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
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
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevChapter) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.action_previous),
                        )
                    }
                    Text(
                        text = stringResource(R.string.reader_page_indicator, currentPage, totalPages.coerceAtLeast(1)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = onNextChapter) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.action_next),
                        )
                    }
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
