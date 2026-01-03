package com.example.soundwave.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.database.SongEntity
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.player.PlayerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val playerManager = PlayerManager.getInstance(application)
    private val musicRepository = AppDatabaseModule.getMusicRepository(application)
    
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()
    
    val isPlaying = playerManager.isPlaying
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration
    
    fun loadSong(songId: Long) {
        viewModelScope.launch {
            _currentSong.value = musicRepository.getSongById(songId)
            _currentSong.value?.let { song ->
                playerManager.playSong(song.filePath, song.id)
            }
        }
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
        // TODO: 実装
    }
    
    fun skipPrevious() {
        // TODO: 実装
    }
}



