package com.techwombat.reader.controls

/** Converts Readium's 0.0–1.0 total progression to a SeekBar-friendly percentage. */
object ProgressionSlider {
    const val MAX_PROGRESS = 100

    fun toSliderProgress(progression: Double?): Int =
        ((progression ?: 0.0).coerceIn(0.0, 1.0) * MAX_PROGRESS).toInt()

    fun toProgression(sliderProgress: Int): Double =
        sliderProgress.coerceIn(0, MAX_PROGRESS).toDouble() / MAX_PROGRESS
}
