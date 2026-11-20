package com.TopMob.bookmt.presentation.reader.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.R
import com.TopMob.bookmt.presentation.theme.ReaderColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top reading HUD status bar: shows chapter title on the left and current clock / battery % on the right.
 */
@Composable
fun ReaderTopStatusBar(
    chapterTitle: String?,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
) {
    val currentTime = rememberCurrentTime()
    val batteryPct = rememberBatteryPercentage()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapterTitle.orEmpty(),
            fontSize = 11.sp,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        val rightMeta = buildString {
            append(currentTime)
            if (batteryPct != null) {
                append(" • ")
                append(batteryPct)
                append("%")
            }
        }

        Text(
            text = rightMeta,
            fontSize = 11.sp,
            color = colors.secondaryText,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Bottom reading HUD status bar: shows page index on the left, percent in the middle, and time remaining on the right.
 */
@Composable
fun ReaderBottomStatusBar(
    currentPage: Int,
    totalPages: Int,
    progressPercent: Int,
    minutesRemaining: Int?,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reader_page_indicator, currentPage, totalPages),
            fontSize = 11.sp,
            color = colors.secondaryText,
            maxLines = 1,
        )

        Text(
            text = "$progressPercent%",
            fontSize = 11.sp,
            color = colors.secondaryText,
            maxLines = 1,
        )

        val remainingText = when {
            minutesRemaining == null || minutesRemaining <= 0 -> ""
            minutesRemaining < 60 -> stringResource(R.string.reader_time_remaining_min, minutesRemaining)
            else -> {
                val hours = minutesRemaining / 60
                val mins = minutesRemaining % 60
                stringResource(R.string.reader_time_remaining_hours_min, hours, mins)
            }
        }

        Text(
            text = remainingText,
            fontSize = 11.sp,
            color = colors.secondaryText,
            maxLines = 1,
        )
    }
}

@Composable
fun rememberBatteryPercentage(): Int? {
    val context = LocalContext.current
    var percentage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            percentage = (level * 100) / scale
        }
    }
    return percentage
}

@Composable
fun rememberCurrentTime(): String {
    var time by remember { mutableStateOf(currentTimeFormatted()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            time = currentTimeFormatted()
        }
    }
    return time
}

private fun currentTimeFormatted(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
