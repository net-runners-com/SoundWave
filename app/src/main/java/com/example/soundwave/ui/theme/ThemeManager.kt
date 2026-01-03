package com.example.soundwave.ui.theme

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme(val displayName: String) {
    PURPLE("パープル"),
    BLUE("ブルー"),
    GREEN("グリーン"),
    ORANGE("オレンジ"),
    RED("レッド")
}

class ThemeManager(private val context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    private val THEME_KEY = "selected_theme"
    
    fun getSelectedTheme(): AppTheme {
        val themeName = prefs.getString(THEME_KEY, AppTheme.PURPLE.name) ?: AppTheme.PURPLE.name
        return try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.PURPLE
        }
    }
    
    fun setSelectedTheme(theme: AppTheme) {
        prefs.edit().putString(THEME_KEY, theme.name).apply()
    }
}

