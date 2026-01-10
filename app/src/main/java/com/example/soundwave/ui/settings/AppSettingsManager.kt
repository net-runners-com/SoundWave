package com.example.soundwave.ui.settings

import android.content.Context
import android.content.SharedPreferences

object AppSettingsManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_BACKGROUND_PLAYBACK = "background_playback"
    private const val KEY_STOP_ON_OTHER_APP = "stop_on_other_app"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isBackgroundPlaybackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_PLAYBACK, true)
    }
    
    fun setBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
    }
    
    fun isStopOnOtherAppEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STOP_ON_OTHER_APP, false)
    }
    
    fun setStopOnOtherAppEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STOP_ON_OTHER_APP, enabled).apply()
    }
}

