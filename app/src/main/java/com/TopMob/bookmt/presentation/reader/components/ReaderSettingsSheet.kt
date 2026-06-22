package com.TopMob.bookmt.presentation.reader.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.ReaderFontFamily
import com.TopMob.bookmt.domain.model.ReaderSettings
import com.TopMob.bookmt.domain.model.ReaderTextAlignment
import com.TopMob.bookmt.domain.model.ReaderTheme
import com.TopMob.bookmt.domain.model.ReadingMode
import java.io.File

/** Bottom-sheet of live reading preferences. Every change persists immediately via [onUpdate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            SectionLabel("Theme")
            ChipRow(
                options = ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM },
                selected = settings.theme,
                label = { it.label() },
                onSelect = { theme -> onUpdate { it.copy(theme = theme) } },
            )

            SectionLabel("Font size: ${settings.fontSizeSp.toInt()}sp")
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { v -> onUpdate { it.copy(fontSizeSp = v) } },
                valueRange = ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
            )

            SectionLabel("Line spacing: ${"%.1f".format(settings.lineSpacingMultiplier)}")
            Slider(
                value = settings.lineSpacingMultiplier,
                onValueChange = { v -> onUpdate { it.copy(lineSpacingMultiplier = v) } },
                valueRange = ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
            )

            SectionLabel("Margins: ${settings.horizontalMarginDp}dp")
            Slider(
                value = settings.horizontalMarginDp.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(horizontalMarginDp = v.toInt()) } },
                valueRange = 0f..64f,
            )

            SectionLabel("Font")
            ChipRow(
                options = ReaderFontFamily.entries,
                selected = settings.fontFamily,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { family -> onUpdate { it.copy(fontFamily = family) } },
            )

            SectionLabel("Alignment")
            ChipRow(
                options = ReaderTextAlignment.entries,
                selected = settings.textAlignment,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { align -> onUpdate { it.copy(textAlignment = align) } },
            )

            SectionLabel("Reading mode")
            ChipRow(
                options = ReadingMode.entries,
                selected = settings.readingMode,
                label = { if (it == ReadingMode.PAGED) "Paged" else "Scroll" },
                onSelect = { mode -> onUpdate { it.copy(readingMode = mode) } },
            )

            SectionLabel(stringResource(R.string.reader_show_slider))
            ToggleRow(
                label = stringResource(R.string.reader_show_slider_desc),
                checked = settings.showProgressSlider,
                onCheckedChange = { on -> onUpdate { it.copy(showProgressSlider = on) } },
            )

            SectionLabel(stringResource(R.string.reader_custom_font))
            Text(
                text = settings.customFontPath?.let { File(it).name }
                    ?: stringResource(R.string.reader_custom_font_default),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onPickFont) {
                    Text(stringResource(R.string.reader_custom_font_pick))
                }
                if (settings.customFontPath != null) {
                    TextButton(onClick = onClearFont) {
                        Text(stringResource(R.string.reader_custom_font_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text = text, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.DAY -> "Day"
    ReaderTheme.SEPIA -> "Sepia"
    ReaderTheme.NIGHT -> "Night"
    ReaderTheme.AMOLED_BLACK -> "AMOLED"
    ReaderTheme.CUSTOM -> "Custom"
}
