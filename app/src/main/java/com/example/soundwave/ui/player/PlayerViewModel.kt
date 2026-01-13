package com.example.soundwave.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.database.LyricsEntity
import com.example.soundwave.data.database.SongEntity
import com.example.soundwave.data.repository.LRCLicSearchResult
import com.example.soundwave.data.repository.LyricLine
import com.example.soundwave.data.repository.LyricsRepository
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.player.PlayerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val playerManager = PlayerManager.getInstance(application)
    private val musicRepository = AppDatabaseModule.getMusicRepository(application)
    private val lyricsRepository = AppDatabaseModule.getLyricsRepository(application)
    
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()
    
    private val _currentLyrics = MutableStateFlow<LyricsEntity?>(null)
    val currentLyrics: StateFlow<LyricsEntity?> = _currentLyrics.asStateFlow()
    
    private val _lyricLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricLines: StateFlow<List<LyricLine>> = _lyricLines.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<LRCLicSearchResult>>(emptyList())
    val searchResults: StateFlow<List<LRCLicSearchResult>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()
    
    private val _lyricsMessage = MutableStateFlow<String?>(null)
    val lyricsMessage: StateFlow<String?> = _lyricsMessage.asStateFlow()
    
    val isPlaying = playerManager.isPlaying
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration
    val repeatMode = playerManager.repeatMode
    val currentSongIdFlow = playerManager.currentSongId
    
    // 現在の再生位置に基づいて表示する歌詞行を計算
    val currentLyricLines: StateFlow<List<LyricLine>> = combine(
        currentLyrics,
        currentPosition
    ) { lyrics, position ->
        if (lyrics != null) {
            lyricsRepository.getLyricLinesAround(lyrics, position, 3)
        } else {
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    fun loadSong(songId: Long) {
        viewModelScope.launch {
            _currentSong.value = musicRepository.getSongById(songId)
            _currentSong.value?.let { song ->
                // 既に再生中の曲の場合は再生を開始しない（位置をリセットしない）
                val currentPlayingSongId = playerManager.currentSongId.value
                if (currentPlayingSongId != songId) {
                    playerManager.playSong(song.filePath, song.id)
                }
                
                // 歌詞を読み込む
                loadLyrics(songId, song.filePath)
            }
        }
    }
    
    fun loadSongInfoOnly(songId: Long) {
        viewModelScope.launch {
            _currentSong.value = musicRepository.getSongById(songId)
            _currentSong.value?.let { song ->
                // 歌詞を読み込む（再生は開始しない）
                loadLyrics(songId, song.filePath)
            }
        }
    }
    
    private suspend fun loadLyrics(songId: Long, songFilePath: String) {
        // まずデータベースから歌詞を取得
        val lyrics = lyricsRepository.getLyrics(songId)
        if (lyrics != null) {
            _currentLyrics.value = lyrics
            if (!lyrics.lyricsLrc.isNullOrEmpty()) {
                _lyricLines.value = lyricsRepository.parseLrcFile(lyrics.lyricsLrc)
            }
        } else {
            // データベースにない場合は、LRCファイルから読み込む
            lyricsRepository.loadLyricsFromFile(songFilePath, songId)
            val loadedLyrics = lyricsRepository.getLyrics(songId)
            _currentLyrics.value = loadedLyrics
            if (loadedLyrics != null && !loadedLyrics.lyricsLrc.isNullOrEmpty()) {
                _lyricLines.value = lyricsRepository.parseLrcFile(loadedLyrics.lyricsLrc)
            }
        }
    }
    
    fun saveLyrics(lyricsText: String, lyricsLrc: String? = null) {
        viewModelScope.launch {
            _currentSong.value?.let { song ->
                val id = lyricsRepository.saveLyrics(song.id, lyricsText, lyricsLrc)
                loadLyrics(song.id, song.filePath)
            }
        }
    }
    
    fun searchLyricsFromLRCLicByKeyword(keyword: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = lyricsRepository.searchLyricsFromLRCLicByKeyword(keyword)
                _searchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error searching lyrics by keyword", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun fetchLyricsFromLRCLic(lyricsId: String) {
        viewModelScope.launch {
            _currentSong.value?.let { song ->
                _isFetchingLyrics.value = true
                _lyricsMessage.value = null
                try {
                    val success = lyricsRepository.fetchAndSaveLyricsFromLRCLicById(song.id, lyricsId)
                    if (success) {
                        loadLyrics(song.id, song.filePath)
                        _lyricsMessage.value = "歌詞を取得しました"
                        // 歌詞取得成功時は検索結果をクリアしない（ダイアログが閉じられるため）
                    } else {
                        _lyricsMessage.value = "歌詞の取得に失敗しました"
                        // 失敗時のみ検索結果をクリア
                        _searchResults.value = emptyList()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error fetching lyrics", e)
                    _lyricsMessage.value = "エラー: ${e.message ?: "歌詞の取得に失敗しました"}"
                    // エラー時も検索結果をクリア
                    _searchResults.value = emptyList()
                } finally {
                    _isFetchingLyrics.value = false
                }
            }
        }
    }
    
    fun clearLyricsMessage() {
        _lyricsMessage.value = null
    }
    
    fun playPause() {
        if (isPlaying.value) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
    }
    
    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }
    
    fun skipNext() {
        playerManager.skipNext()
    }
    
    fun skipPrevious() {
        playerManager.skipPrevious()
    }
    
    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }
    
    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }
}



