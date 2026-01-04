package com.example.soundwave.widget

import android.content.Context
import android.content.SharedPreferences

enum class WidgetTheme {
    LIGHT,
    DARK,
    SYSTEM,
    ALBUM_ART
}

enum class WidgetContentType {
    SIMPLE,
    ADVANCED,
    CURRENT_QUEUE
}

data class WidgetSettings(
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val transparency: Int = 0, // 0-100
    val contentType: WidgetContentType = WidgetContentType.SIMPLE,
    val showAlbumArt: Boolean = true,
    val showAddToPlaylist: Boolean = false
)

object WidgetSettingsManager {
    private const val PREFS_NAME = "widget_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_TRANSPARENCY = "transparency"
    private const val KEY_CONTENT_TYPE = "content_type"
    private const val KEY_SHOW_ALBUM_ART = "show_album_art"
    private const val KEY_SHOW_ADD_TO_PLAYLIST = "show_add_to_playlist"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveSettings(context: Context, settings: WidgetSettings) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putString(KEY_THEME, settings.theme.name)
            putInt(KEY_TRANSPARENCY, settings.transparency)
            putString(KEY_CONTENT_TYPE, settings.contentType.name)
            putBoolean(KEY_SHOW_ALBUM_ART, settings.showAlbumArt)
            putBoolean(KEY_SHOW_ADD_TO_PLAYLIST, settings.showAddToPlaylist)
            apply()
        }
    }
    
    fun loadSettings(context: Context): WidgetSettings {
        val prefs = getPrefs(context)
        return WidgetSettings(
            theme = try {
                WidgetTheme.valueOf(prefs.getString(KEY_THEME, WidgetTheme.SYSTEM.name) ?: WidgetTheme.SYSTEM.name)
            } catch (e: Exception) {
                WidgetTheme.SYSTEM
            },
            transparency = prefs.getInt(KEY_TRANSPARENCY, 0).coerceIn(0, 100),
            contentType = try {
                WidgetContentType.valueOf(prefs.getString(KEY_CONTENT_TYPE, WidgetContentType.SIMPLE.name) ?: WidgetContentType.SIMPLE.name)
            } catch (e: Exception) {
                WidgetContentType.SIMPLE
            },
            showAlbumArt = prefs.getBoolean(KEY_SHOW_ALBUM_ART, true),
            showAddToPlaylist = prefs.getBoolean(KEY_SHOW_ADD_TO_PLAYLIST, false)
        )
    }
}

