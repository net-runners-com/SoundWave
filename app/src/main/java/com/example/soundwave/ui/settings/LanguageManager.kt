package com.example.soundwave.ui.settings

import android.content.Context
import android.content.SharedPreferences

object LanguageManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "app_language"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getSelectedLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "ja") ?: "ja"
    }
    
    fun setLanguage(context: Context, languageCode: String) {
        // 言語設定を保存（Activity.recreate()により、attachBaseContextで自動的に適用される）
        getPrefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
    }
    
    fun getSupportedLanguages(): List<Language> {
        return listOf(
            Language("ja", "日本語", "Japanese"),
            Language("en", "English", "English"),
            Language("ko", "한국어", "Korean"),
            Language("zh", "中文", "Chinese")
        )
    }
    
    data class Language(
        val code: String,
        val nativeName: String,
        val englishName: String
    )
}

