package com.techwombat.reader.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences",
)

enum class ReaderTheme {
    LIGHT,
    DARK,
}

data class ReaderAppearance(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
) {
    fun normalized(): ReaderAppearance = copy(
        fontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        lineSpacing = lineSpacing.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        fontFamily = fontFamily.ifBlank { DEFAULT_FONT_FAMILY },
    )

    companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_FONT_FAMILY = "serif"
        const val DEFAULT_LINE_SPACING = 1.5f
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 2.0f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.2f
    }
}

class ReaderPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.readerPreferencesDataStore)

    val appearance: Flow<ReaderAppearance> = dataStore.data.map { preferences ->
        ReaderAppearance(
            fontScale = preferences[FONT_SCALE] ?: ReaderAppearance.DEFAULT_FONT_SCALE,
            fontFamily = preferences[FONT_FAMILY] ?: ReaderAppearance.DEFAULT_FONT_FAMILY,
            lineSpacing = preferences[LINE_SPACING] ?: ReaderAppearance.DEFAULT_LINE_SPACING,
            theme = preferences[THEME]
                ?.let { saved -> ReaderTheme.entries.firstOrNull { it.name == saved } }
                ?: ReaderTheme.LIGHT,
        ).normalized()
    }

    suspend fun saveAppearance(appearance: ReaderAppearance) {
        val normalized = appearance.normalized()
        dataStore.edit { preferences ->
            preferences[FONT_SCALE] = normalized.fontScale
            preferences[FONT_FAMILY] = normalized.fontFamily
            preferences[LINE_SPACING] = normalized.lineSpacing
            preferences[THEME] = normalized.theme.name
        }
    }

    private companion object {
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val THEME = stringPreferencesKey("theme")
    }
}
