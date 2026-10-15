package com.TopMob.bookmt.domain.model

import org.junit.Assert.*
import org.junit.Test

class TextAnchorsTest {
    private fun book(vararg parts: String): BookContent {
        var offset = 0
        return BookContent("Book", null, parts.mapIndexed { index, text ->
            Chapter(index, "Part", text, offset, "same-source", offset).also { offset += text.length }
        }, TableOfContents.EMPTY)
    }
    @Test fun contextSurvivesInsertedHeadingAndChangedWhitespace() {
        val old = book("Вступление. Это важная цитата для проверки, которая продолжается дальше. Конец.")
        val position = old.chapters[0].text.indexOf("важная")
        val anchor = TextAnchors.capture(old, position)!!.encode()
        val updated = book("Новый заголовок\nВступление. Это  важная\nцитата для проверки, которая продолжается дальше. Конец.")
        val result = TextAnchors.resolve(updated, anchor, position)
        assertTrue(result.matched)
        assertEquals(updated.chapters[0].text.indexOf("важная"), result.offset)
    }
    @Test fun anchorCrossesStorageBlockBoundaryAndPreservesEndPosition() {
        val old = book("Начало. " + "Предисловие ".repeat(8), "Выбранная цитата с уникальным продолжением. Конец.")
        val offset = old.chapters[1].startOffset + 5
        val anchor = TextAnchors.capture(old, offset)!!.encode()
        val changed = book("Заголовок.\n" + old.chapters[0].text + "Вы", old.chapters[1].text.drop(2))
        val result = TextAnchors.resolve(changed, anchor, offset)
        assertTrue(result.matched)
        assertEquals(offset + "Заголовок.\n".length, result.offset)
        val end = TextAnchors.capture(changed, changed.totalLength)!!.encode()
        assertEquals(changed.totalLength, TextAnchors.resolve(changed, end, 0).offset)
    }
    @Test fun missingOrDamagedAnchorKeepsBoundedFallback() {
        val content = book("Короткая книга")
        assertFalse(TextAnchors.resolve(content, "broken", 500).matched)
        assertEquals(content.totalLength, TextAnchors.resolve(content, "broken", 500).offset)
        val anchor = TextAnchor("глава+один/2", 8, "до\tстроки", "после😀\nстроки")
        assertEquals(anchor, TextAnchor.decode(anchor.encode()))
    }

    @Test fun insertedSectionHeadingDoesNotInvalidateUniqueQuoteAnchor() {
        val old = book("Последние слова предыдущего раздела. Уникальная цитата и её достаточно длинное продолжение. Далее идут новые события.")
        val offset = old.chapters[0].text.indexOf("Уникальная")
        val anchor = TextAnchors.capture(old, offset)!!.encode()
        val inserted = "\nНОВЫЙ ЗАГОЛОВОК ГЛАВЫ\n"
        val updated = book(old.chapters[0].text.substring(0, offset) + inserted + old.chapters[0].text.substring(offset))
        val resolved = TextAnchors.resolve(updated, anchor, offset)
        assertTrue(resolved.matched)
        assertEquals(offset + inserted.length, resolved.offset)
    }

    @Test fun exclusiveSelectionEndDoesNotAbsorbWhitespaceAfterMigration() {
        val sentence = "Уникальная цитата для проверки выделения и его точной границы."
        val old = book("До. " + sentence)
        val end = old.chapters[0].text.indexOf(" для")
        val anchor = TextAnchors.capture(old, end)!!.encode()
        val updated = book("Новый заголовок. До. " + sentence.replace(" для", "\n  для"))
        val resolved = TextAnchors.resolve(updated, anchor, end)
        assertTrue(resolved.matched)
        assertEquals(updated.chapters[0].text.indexOf("\n"), resolved.offset)
    }
}
