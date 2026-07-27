package com.techwombat.reader.appearance

import com.techwombat.reader.storage.ReaderAppearance
import com.techwombat.reader.storage.ReaderTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

object ReaderAppearanceMapper {
    val fontChoices = listOf("serif" to "Serif", "sans-serif" to "Sans-serif", "monospace" to "Monospace", "cursive" to "Cursive", "open-dyslexic" to "OpenDyslexic", "accessible-dfa" to "AccessibleDfA", "ia-writer-duospace" to "iA Writer Duospace")
    fun toEpubPreferences(appearance: ReaderAppearance): EpubPreferences {
        val normalized = appearance.normalized()
        return EpubPreferences(
            fontFamily = when (normalized.fontFamily) {
                "sans-serif" -> FontFamily.SANS_SERIF; "monospace" -> FontFamily.MONOSPACE; "cursive" -> FontFamily.CURSIVE
                "open-dyslexic" -> FontFamily.OPEN_DYSLEXIC; "accessible-dfa" -> FontFamily.ACCESSIBLE_DFA; "ia-writer-duospace" -> FontFamily.IA_WRITER_DUOSPACE
                else -> FontFamily.SERIF
            },
            fontSize = normalized.fontScale.toDouble(), lineHeight = normalized.lineSpacing.toDouble(),
            paragraphSpacing = normalized.paragraphSpacing.toDouble(), pageMargins = 1.5, publisherStyles = false,
            theme = if (normalized.theme == ReaderTheme.DARK) Theme.DARK else Theme.LIGHT,
        )
    }
}
