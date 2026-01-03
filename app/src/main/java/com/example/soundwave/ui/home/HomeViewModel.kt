package com.example.soundwave.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.database.SongEntity
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.data.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepository: MusicRepository = AppDatabaseModule.getMusicRepository(application)
    private val playlistRepository: PlaylistRepository = AppDatabaseModule.getPlaylistRepository(application)
    
    // キャッシュ用のStateFlow
    private val _songsCache = MutableStateFlow<List<com.example.soundwave.data.database.SongEntity>>(emptyList())
    private val _albumsCache = MutableStateFlow<List<String>>(emptyList())
    private val _artistsCache = MutableStateFlow<List<String>>(emptyList())
    private val _foldersCache = MutableStateFlow<List<String>>(emptyList())
    private val _playlistsCache = MutableStateFlow<List<com.example.soundwave.data.database.PlaylistEntity>>(emptyList())
    
    val songs: StateFlow<List<com.example.soundwave.data.database.SongEntity>> = _songsCache.asStateFlow()
    val albums: StateFlow<List<String>> = _albumsCache.asStateFlow()
    val artists: StateFlow<List<String>> = _artistsCache.asStateFlow()
    val folders: StateFlow<List<String>> = _foldersCache.asStateFlow()
    val playlists: StateFlow<List<com.example.soundwave.data.database.PlaylistEntity>> = _playlistsCache.asStateFlow()
    
    // Paging対応: 大量データ用
    val songsPaged: Flow<PagingData<SongEntity>> = musicRepository.getAllSongsPaged(viewModelScope)
    
    // 最適化: 最近の曲
    private val _recentSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val recentSongs: StateFlow<List<SongEntity>> = _recentSongs.asStateFlow()
    
    // 最適化: 人気の曲
    private val _popularSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val popularSongs: StateFlow<List<SongEntity>> = _popularSongs.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // データを事前取得してキャッシュに保存
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 初回データ取得を待つ
                val initialSongs = musicRepository.getAllSongs().first()
                if (initialSongs.isEmpty()) {
                    // データがない場合はスキャンを実行
                    scanMusicFiles()
                } else {
                    _isLoading.value = false
                }
                
                // データを継続的に監視してキャッシュに保存
                launch {
                    musicRepository.getAllSongs().collect { 
                        _songsCache.value = it
                        if (_isLoading.value && it.isNotEmpty()) {
                            _isLoading.value = false
                        }
                    }
                }
                launch {
                    musicRepository.getAllAlbums().collect { _albumsCache.value = it }
                }
                launch {
                    musicRepository.getAllArtists().collect { _artistsCache.value = it }
                }
                launch {
                    musicRepository.getAllFolders().collect { _foldersCache.value = it }
                }
                launch {
                    playlistRepository.getAllPlaylists().collect { _playlistsCache.value = it }
                }
                
                // バックグラウンドで最近の曲と人気の曲を取得
                launch(Dispatchers.IO) {
                    _recentSongs.value = musicRepository.getRecentSongs(50)
                }
                launch(Dispatchers.IO) {
                    _popularSongs.value = musicRepository.getPopularSongs(50)
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }
    
    fun scanMusicFiles() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                musicRepository.scanMusicFiles()
                _isLoading.value = false
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    // プレイリスト作成
    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistRepository.createPlaylist(name)
                // プレイリスト一覧は自動的に更新される（Flowで監視中）
            } catch (e: Exception) {
                // エラーハンドリング（必要に応じてエラー状態を管理）
            }
        }
    }
}
