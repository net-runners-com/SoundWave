package com.example.soundwave.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchHistoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("youtube_search_history", Context.MODE_PRIVATE)
    private val maxHistorySize = 20
    
    suspend fun getSearchHistory(): List<String> = withContext(Dispatchers.IO) {
        val historyJson = prefs.getString("history", "[]") ?: "[]"
        try {
            val list = mutableListOf<String>()
            val jsonArray = org.json.JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun addSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        
        val currentHistory = getSearchHistory().toMutableList()
        
        // 既に存在する場合は削除して先頭に追加
        currentHistory.remove(query)
        currentHistory.add(0, query)
        
        // 最大件数を超えた場合は古いものを削除
        if (currentHistory.size > maxHistorySize) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        val jsonArray = org.json.JSONArray()
        currentHistory.forEach { jsonArray.put(it) }
        
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }
    
    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        prefs.edit().putString("history", "[]").apply()
    }
    
    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val currentHistory = getSearchHistory().toMutableList()
        currentHistory.remove(query)
        
        val jsonArray = org.json.JSONArray()
        currentHistory.forEach { jsonArray.put(it) }
        
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }
}

