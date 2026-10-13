package com.TopMob.bookmt

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.reader.components.ReaderContent
import com.TopMob.bookmt.presentation.theme.ReaderColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedPositionAndBookmarkJumpMovePagedSurface() {
        val offset = mutableStateOf(5000)
        val version = mutableStateOf(0L)
        var reported = -1
        var page = 0
        val text = "Example reading text with words and spaces. ".repeat(400)
        val content = BookContent("Title", null, listOf(Chapter(0, "Chapter", text, 0)), TableOfContents.EMPTY)
        compose.setContent {
            ReaderContent(content, TextStyle(fontSize = 18.sp, color = Color.Black),
                ReaderColors.forTheme(ReaderTheme.DAY), ReadingMode.PAGED, 16, 16,
                offset.value, version.value, { reported = it; offset.value = it },
                { current, _ -> page = current }, {}, {}, Modifier.size(320.dp, 480.dp))
        }
        compose.waitUntil(30_000) { reported in 1..5000 && page > 1 }
        compose.runOnIdle { offset.value = 12000; version.value++ }
        compose.waitUntil(30_000) { reported in 10000..12000 }
        compose.runOnIdle { offset.value = 0; version.value++ }
        compose.waitUntil(30_000) { reported == 0 && page == 1 }
    }

    @Test fun scrollRestoresWithinLongChapterAndModeSwitchKeepsPosition() {
        val offset = mutableStateOf(7000)
        val mode = mutableStateOf(ReadingMode.SCROLL)
        var reported = -1
        val text = "One line of reading text.\n".repeat(800)
        val content = BookContent("Title", null, listOf(Chapter(0, "Chapter", text, 0)), TableOfContents.EMPTY)
        compose.setContent {
            ReaderContent(content, TextStyle(fontSize = 18.sp), ReaderColors.forTheme(ReaderTheme.DAY),
                mode.value, 16, 16, offset.value, 0, { reported = it; offset.value = it },
                { _, _ -> }, {}, {}, Modifier.size(320.dp, 480.dp))
        }
        compose.waitUntil(30_000) { reported in 6500..7000 }
        compose.runOnIdle { mode.value = ReadingMode.PAGED }
        compose.waitUntil(30_000) { reported in 6000..7000 }
        compose.runOnIdle { assertTrue(reported > 0) }
    }
}
