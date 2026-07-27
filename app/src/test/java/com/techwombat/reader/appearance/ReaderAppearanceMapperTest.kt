package com.techwombat.reader.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAppearanceMapperTest {
    @Test
    fun exposesAllBuiltInFontChoicesIncludingOpenDyslexic() {
        assertEquals(7, ReaderAppearanceMapper.fontChoices.size)
        assertTrue(ReaderAppearanceMapper.fontChoices.any { it.first == "open-dyslexic" })
    }
}
