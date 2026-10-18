package com.TopMob.bookmt

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.TopMob.bookmt.domain.model.*
import com.TopMob.bookmt.presentation.reader.components.ReaderContent
import com.TopMob.bookmt.presentation.reader.components.ReaderPageText
import com.TopMob.bookmt.presentation.reader.ReaderPage
import com.TopMob.bookmt.presentation.theme.ReaderColors
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderNavigationTest {
    @get:Rule val compose = createComposeRule()


    @Test fun pageButtonsStartAtFirstLineThatWasNotFullyVisible() = checkPageButtonGeometry(18)
    @Test fun volumePagingUsesWholeReadableScreenWithLargeText() = checkPageButtonGeometry(29)

    private fun checkPageButtonGeometry(fontSp: Int) {
        val text = (0..500).joinToString("\n") { "Row ${it.toString().padStart(4, '0')}: a quiet evening reading a local book." }
        val content = BookContent("Paging", null, listOf(Chapter(0, "Chapter", text, 0)), TableOfContents.EMPTY)
        val offset = mutableStateOf(0)
        var total = 0
        var turn: ((Int) -> Unit)? = null
        var volume: ((Int) -> Unit)? = null
        compose.setContent {
            ReaderContent(content, TextStyle(fontSize = fontSp.sp, lineHeight = (fontSp * 1.4f).sp),
                ReaderColors.forTheme(ReaderTheme.DAY), ReadingMode.SCROLL, 16, 19,
                offset.value, 0, { offset.value = it }, { _, count -> total = count },
                { turn = it }, {}, Modifier.size(320.dp, 480.dp).testTag("paging-reader"),
                onRegisterLineScroll = { volume = it })
        }
        compose.waitUntil(30_000) { total > 0 && turn != null && volume != null }
        compose.waitForIdle()
        fun layouts(): List<Pair<androidx.compose.ui.semantics.SemanticsNode, androidx.compose.ui.text.TextLayoutResult>> =
            compose.onAllNodes(hasText("Row", substring = true)).fetchSemanticsNodes().mapNotNull { node ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.let { node to it }
            }
        repeat(4) { step ->
            val bounds = compose.onNodeWithTag("paging-reader").fetchSemanticsNode().boundsInRoot
            val margin = bounds.width * 19f / 320f
            val bottom = bounds.bottom - margin
            val candidates = layouts().flatMap { (node, layout) ->
                val start = text.indexOf(layout.layoutInput.text.text)
                assertTrue("Page must correspond to original source", start >= 0)
                (0 until layout.lineCount).filter { layout.getLineStart(it) < layout.layoutInput.text.length && node.positionInRoot.y + layout.getLineBottom(it) > bottom + 0.5f }
                    .map { start + layout.getLineStart(it) }
            }
            assertTrue("Need a clipped or following line", candidates.isNotEmpty())
            val target = candidates.min()
            compose.runOnIdle { (if (step % 2 == 0) turn else volume)?.invoke(1) }
            try { compose.waitUntil(15_000) {
                layouts().any { (node, layout) ->
                    val start = text.indexOf(layout.layoutInput.text.text)
                    if (target !in start until start + layout.layoutInput.text.length) false
                    else kotlin.math.abs(node.positionInRoot.y + layout.getLineTop(layout.getLineForOffset(target - start)) - (bounds.top + margin)) <= 2f
                }
            } } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Step=$step target=$target top=${bounds.top + margin}; pages=" + layouts().map { (node, layout) ->
                    val start = text.indexOf(layout.layoutInput.text.text)
                    "start=$start y=${node.positionInRoot.y} lines=${layout.lineCount} targetY=" +
                        if (target in start until start + layout.layoutInput.text.length) (node.positionInRoot.y + layout.getLineTop(layout.getLineForOffset(target - start))) else "outside"
                }, failure)
            }
        }
        val beforeBack = offset.value
        compose.runOnIdle { turn?.invoke(-1) }
        compose.waitUntil(15_000) { offset.value < beforeBack }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("paging-reader").fetchSemanticsNode().boundsInRoot
        val top = bounds.top + bounds.width * 19f / 320f
        assertTrue("Previous page must also start at a whole line", layouts().any { (node, layout) ->
            (0 until layout.lineCount).any { kotlin.math.abs(node.positionInRoot.y + layout.getLineTop(it) - top) <= 2f }
        })
    }

    @Test fun unloadedPagesKeepLazyCompositionBounded() {
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val text = "A visible page of text. ".repeat(15)
        val chapter = Chapter(0, "Chapter", text.repeat(200), 0)
        val chapters = object : AbstractList<Chapter>(), ChapterCollection {
            override val size = 1
            override fun descriptor(index: Int) = chapter.descriptor()
            override fun get(index: Int): Chapter {
                reads.incrementAndGet()
                Thread.sleep(25)
                return chapter
            }
        }
        val content = BookContent("Book", null, chapters, TableOfContents.EMPTY)
        compose.setContent {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.size(320.dp, 480.dp)) {
                items(200) { index ->
                    ReaderPageText(ReaderPage(content, 0, index * text.length, (index + 1) * text.length),
                        TextStyle(fontSize = 18.sp), emptyList(), emptyList(), {}, { _, _ -> },
                        viewportHeightPx = 900)
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("A visible page", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertTrue("Loaded ${reads.get()} pages for one viewport", reads.get() < 16) }
    }

    @Test fun scrollRestoresTheVisibleLineAfterClosingAndReopening() = checkVisibleLineRestoration(0)
    @Test fun expandingEarlierChaptersKeepsTheVisibleLineAfterReopening() = checkVisibleLineRestoration(3)

    private fun checkVisibleLineRestoration(chapterIndex: Int) {
        val shown = mutableStateOf(true)
        val saved = mutableStateOf(0)
        val audio = mutableStateOf(true)
        val texts = (0..5).map { index -> (0..160).joinToString("\n") { "C$index Line $it: readable words for restoration." } }
        val marker = "C$chapterIndex Line 45:"
        val target = chapterIndex * texts[0].length + texts[chapterIndex].indexOf(marker)
        saved.value = target
        val content = BookContent("Title", null,
            texts.mapIndexed { index, text -> Chapter(index, "Chapter $index", text, index * text.length) }, TableOfContents.EMPTY)
        var total = 0
        compose.setContent {
            if (shown.value) ReaderContent(content, TextStyle(fontSize = 18.sp),
                ReaderColors.forTheme(ReaderTheme.DAY), ReadingMode.SCROLL, 16, 16,
                saved.value, 0, { saved.value = it }, { _, count -> total = count }, {}, {},
                Modifier.size(320.dp, 480.dp).testTag("reader"), prioritizeAudio = audio.value)
        }
        fun assertVisibleTarget() {
            compose.waitUntil(30_000) {
                val nodes = compose.onAllNodes(hasText(marker, substring = true)).fetchSemanticsNodes()
                nodes.any { node ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                    val layout = results.firstOrNull() ?: return@any false
                    val local = layout.layoutInput.text.text.indexOf(marker)
                    val y = node.positionInRoot.y + layout.getLineTop(layout.getLineForOffset(local))
                    val bounds = compose.onNodeWithTag("reader").fetchSemanticsNode().boundsInRoot
                    val expectedY = bounds.top + bounds.width * 16f / 320f
                    kotlin.math.abs(y - expectedY) <= 2f
                }
            }
        }
        assertVisibleTarget()
        compose.waitUntil(30_000) { total < 0 }
        compose.runOnIdle { audio.value = false }
        compose.waitUntil(30_000) { total > 0 }
        assertVisibleTarget() // book-wide expansion must retain the actual visible line
        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { shown.value = true }
        assertVisibleTarget()
        compose.runOnIdle { assertEquals(target, saved.value) }
    }

    @Test fun pagedCounterCompletesWithoutTurningThePage() = checkPaginationCompletion(ReadingMode.PAGED)
    @Test fun scrollCounterCompletesWithoutScrolling() = checkPaginationCompletion(ReadingMode.SCROLL)

    private fun checkPaginationCompletion(mode: ReadingMode) {
        val audio = mutableStateOf(true)
        val reportedTotal = java.util.concurrent.atomic.AtomicInteger(0)
        val text = "Reading text with spaces. ".repeat(30)
        val chapters = (0..4).map { Chapter(it, "Chapter $it", text, it * text.length) }
        val content = BookContent("Title", null, chapters, TableOfContents.EMPTY)
        compose.setContent {
            ReaderContent(content, TextStyle(fontSize = 18.sp), ReaderColors.forTheme(ReaderTheme.DAY), mode,
                16, 16, 0, 0, {}, { _, total -> reportedTotal.set(total) }, {}, {},
                Modifier.size(320.dp, 480.dp), prioritizeAudio = audio.value)
        }
        compose.waitUntil(30_000) { reportedTotal.get() < 0 }
        compose.runOnIdle { audio.value = false }
        compose.waitUntil(30_000) { reportedTotal.get() > 0 }
    }

    @Test fun structuredFormattingAndSpokenPhraseAreVisibleTogether() {
        val text = "Первое предложение. Второе предложение."
        val offset = text.indexOf("Второе")
        val content = BookContent("Title", null, listOf(Chapter(0, "Chapter", text, 0,
            spans = listOf(ContentSpan(0, 6, ContentStyle.BOLD)))), TableOfContents.EMPTY)
        compose.setContent {
            ReaderContent(content, TextStyle(fontSize = 18.sp), ReaderColors.forTheme(ReaderTheme.DAY), ReadingMode.PAGED,
                16, 16, 0, 0, {}, { _, _ -> }, {}, {}, Modifier.size(320.dp, 480.dp),
                spokenSegment = TextSegment(text.substring(offset), SpeakerRole.NARRATOR, offset))
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        val annotated = compose.onNode(hasText(text)).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 6 })
        assertTrue(annotated.spanStyles.any { it.item.background != Color.Unspecified && it.start == offset && it.end == text.length })
    }

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
