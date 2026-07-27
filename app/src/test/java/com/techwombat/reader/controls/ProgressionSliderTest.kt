package com.techwombat.reader.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionSliderTest {
    @Test
    fun mapsBookProgressionToSliderAtStartMiddleAndEnd() {
        assertEquals(0, ProgressionSlider.toSliderProgress(0.0))
        assertEquals(50, ProgressionSlider.toSliderProgress(0.5))
        assertEquals(100, ProgressionSlider.toSliderProgress(1.0))
    }

    @Test
    fun mapsSliderValuesToBookProgressionAtStartMiddleAndEnd() {
        assertEquals(0.0, ProgressionSlider.toProgression(0), 0.0)
        assertEquals(0.5, ProgressionSlider.toProgression(50), 0.0)
        assertEquals(1.0, ProgressionSlider.toProgression(100), 0.0)
    }

    @Test
    fun clampsOutOfRangeValues() {
        assertEquals(0, ProgressionSlider.toSliderProgress(-1.0))
        assertEquals(100, ProgressionSlider.toSliderProgress(2.0))
        assertEquals(0.0, ProgressionSlider.toProgression(-5), 0.0)
        assertEquals(1.0, ProgressionSlider.toProgression(105), 0.0)
    }
}
