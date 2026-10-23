package com.TopMob.bookmt.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.model.MotionMode
import com.TopMob.bookmt.presentation.theme.LocalComfortMotion
import kotlinx.coroutines.delay

/** T04: no flashing spinner for short operations, and no perpetual loop in reduced-motion mode. */
@Composable
fun ComfortLoadingIndicator(modifier: Modifier = Modifier, label: String = stringResource(R.string.comfort_loading),
    compact: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true }
    val motion = LocalComfortMotion.current
    Box(modifier, contentAlignment = Alignment.Center) {
        if (show) {
            if (motion.systemEnabled && motion.mode == MotionMode.SYSTEM) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(if (compact) 18.dp else 32.dp),
                        strokeWidth = if (compact) 2.dp else 3.dp)
                    if (!compact) Text(label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ComfortEmptyState(title: String, message: String, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.padding(24.dp).widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        actionLabel?.let { Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(it) } }
    }
}

/** Error text stays visible; action belongs to the caller's actual recovery path. */
@Composable
fun ComfortErrorState(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp).widthIn(max = 480.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
    }
}
