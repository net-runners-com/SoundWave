package com.example.soundwave.ui.home.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundwave.data.repository.YouTubeRepository
import com.example.soundwave.data.repository.YouTubeVideo
import com.example.soundwave.util.SearchHistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YouTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val searchHistoryManager = SearchHistoryManager(application)
    
    private val _searchResults = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val searchResults: StateFlow<List<YouTubeVideo>> = _searchResults.asStateFlow()
    
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        loadSearchHistory()
    }
    
    private fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = searchHistoryManager.getSearchHistory()
        }
    }
    
    fun searchVideos(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val results = repository.searchVideos(query)
                _searchResults.value = results
                
                // 検索履歴に追加
                searchHistoryManager.addSearchQuery(query)
                loadSearchHistory()
                
                if (results.isEmpty()) {
                    _errorMessage.value = "検索結果が見つかりませんでした"
                }
            } catch (e: Exception) {
                _errorMessage.value = "検索中にエラーが発生しました: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearResults() {
        _searchResults.value = emptyList()
        _errorMessage.value = null
    }
    
    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryManager.clearSearchHistory()
            loadSearchHistory()
        }
    }
    
    fun removeSearchQuery(query: String) {
        viewModelScope.launch {
            searchHistoryManager.removeSearchQuery(query)
            loadSearchHistory()
        }
    }
}

