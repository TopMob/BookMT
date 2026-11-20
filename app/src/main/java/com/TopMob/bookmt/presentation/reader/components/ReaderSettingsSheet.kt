package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.ReaderFontFamily
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.model.ReaderTheme
import com.TopMob.bookmt.domain.model.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Theme selector
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ReaderTheme.DAY to R.string.settings_theme_day,
                    ReaderTheme.SEPIA to R.string.settings_theme_sepia,
                    ReaderTheme.NIGHT to R.string.settings_theme_night,
                    ReaderTheme.AMOLED_BLACK to R.string.settings_theme_amoled,
                ).forEach { (theme, labelRes) ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onUpdateSettings { it.copy(theme = theme) } },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reading Mode: Paged vs Scroll
            Text(
                text = stringResource(R.string.settings_reading_mode),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.readingMode == ReadingMode.PAGED,
                    onClick = { onUpdateSettings { it.copy(readingMode = ReadingMode.PAGED) } },
                    label = { Text(stringResource(R.string.settings_mode_paged)) },
                )
                FilterChip(
                    selected = settings.readingMode == ReadingMode.SCROLL,
                    onClick = { onUpdateSettings { it.copy(readingMode = ReadingMode.SCROLL) } },
                    label = { Text(stringResource(R.string.settings_mode_scroll)) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size, settings.fontSizeSp.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSizeSp - 1f).coerceAtLeast(ReaderSettings.MIN_FONT_SIZE_SP)
                            onUpdateSettings { it.copy(fontSizeSp = newSize) }
                        },
                        enabled = settings.fontSizeSp > ReaderSettings.MIN_FONT_SIZE_SP,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                    }
                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSizeSp + 1f).coerceAtMost(ReaderSettings.MAX_FONT_SIZE_SP)
                            onUpdateSettings { it.copy(fontSizeSp = newSize) }
                        },
                        enabled = settings.fontSizeSp < ReaderSettings.MAX_FONT_SIZE_SP,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Line spacing
            Text(
                text = stringResource(R.string.settings_line_spacing, String.format("%.1fx", settings.lineSpacingMultiplier)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1.2f, 1.4f, 1.6f, 1.8f).forEach { spacing ->
                    FilterChip(
                        selected = kotlin.math.abs(settings.lineSpacingMultiplier - spacing) < 0.05f,
                        onClick = { onUpdateSettings { it.copy(lineSpacingMultiplier = spacing) } },
                        label = { Text(String.format("%.1fx", spacing)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Margins
            Text(
                text = stringResource(R.string.settings_margins, settings.horizontalMarginDp),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(12, 16, 24, 32).forEach { margin ->
                    FilterChip(
                        selected = settings.horizontalMarginDp == margin,
                        onClick = { onUpdateSettings { it.copy(horizontalMarginDp = margin) } },
                        label = { Text("${margin}dp") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Family
            Text(
                text = stringResource(R.string.settings_font_family),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ReaderFontFamily.SERIF to R.string.settings_font_serif,
                    ReaderFontFamily.SANS_SERIF to R.string.settings_font_sans,
                    ReaderFontFamily.MONOSPACE to R.string.settings_font_mono,
                ).forEach { (family, labelRes) ->
                    FilterChip(
                        selected = settings.fontFamily == family,
                        onClick = { onUpdateSettings { it.copy(fontFamily = family) } },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggles: Status bar & Volume key navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_show_status_bar),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_show_status_bar_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = settings.showStatusBar,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(showStatusBar = checked) }
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_volume_keys),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_volume_keys_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = settings.volumeKeyNavigation,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(volumeKeyNavigation = checked) }
                    },
                )
            }
        }
    }
}
