package com.techwombat.reader.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStorageModelsTest {
    @Test
    fun defaultAppearanceIsComfortableLightSerifReading() {
        val appearance = ReaderAppearance()

        assertEquals(1.0f, appearance.fontScale)
        assertEquals("serif", appearance.fontFamily)
        assertEquals(1.5f, appearance.lineSpacing)
        assertEquals(ReaderTheme.LIGHT, appearance.theme)
    }

    @Test
    fun appearanceNormalizesOutOfRangeValuesWithoutChangingTheme() {
        val normalized = ReaderAppearance(
            fontScale = 9.0f,
            fontFamily = "",
            lineSpacing = 0.1f,
            theme = ReaderTheme.DARK,
        ).normalized()

        assertEquals(ReaderAppearance.MAX_FONT_SCALE, normalized.fontScale)
        assertEquals(ReaderAppearance.DEFAULT_FONT_FAMILY, normalized.fontFamily)
        assertEquals(ReaderAppearance.MIN_LINE_SPACING, normalized.lineSpacing)
        assertEquals(ReaderTheme.DARK, normalized.theme)
    }

    @Test
    fun bookStateKeepsLocationSeparateFromSourceIdentity() {
        val state = BookReadingState(
            bookId = "book-hash-1",
            sourceUri = "content://books/example",
            privateFilePath = null,
            title = "Example",
            locatorJson = "{\"href\":\"chapter-2.xhtml\"}",
            totalProgression = 0.42,
            lastOpenedAtEpochMillis = 1234L,
        )

        assertEquals("book-hash-1", state.bookId)
        assertEquals(0.42, state.totalProgression)
        assertNull(state.privateFilePath)
    }
}
