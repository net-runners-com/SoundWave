package com.example.soundwave.player

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AudioEffectSettingsManager {
    private const val PREFS_NAME = "audio_effects_prefs"
    private const val KEY_SETTINGS = "audio_effect_settings"
    
    private val gson = Gson()
    
    fun saveSettings(context: Context, settings: AudioEffectSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }
    
    fun loadSettings(context: Context): AudioEffectSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SETTINGS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AudioEffectSettings::class.java)
            } catch (e: Exception) {
                android.util.Log.e("AudioEffectSettingsManager", "Failed to load settings", e)
                AudioEffectSettings()
            }
        } else {
            AudioEffectSettings()
        }
    }
}

