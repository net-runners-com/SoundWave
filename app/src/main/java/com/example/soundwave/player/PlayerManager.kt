package com.example.soundwave.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager private constructor(
    private val context: Context
) : DefaultLifecycleObserver {
    
    companion object {
        @Volatile
        private var INSTANCE: PlayerManager? = null
        
        fun getInstance(context: Context): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var service: MusicPlayerService? = null
    private var isBound = false
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId: StateFlow<Long?> = _currentSongId.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicPlayerService.MusicBinder
            this@PlayerManager.service = binder?.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }
    
    init {
        startAndBindService()
    }
    
    private fun startAndBindService() {
        val intent = Intent(context, MusicPlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    fun playSong(filePath: String, songId: Long) {
        service?.playSong(filePath, songId)
        _currentSongId.value = songId
        _isPlaying.value = true
    }
    
    fun pause() {
        service?.pause()
        _isPlaying.value = false
    }
    
    fun resume() {
        service?.resume()
        _isPlaying.value = true
    }
    
    fun seekTo(position: Long) {
        service?.seekTo(position)
    }
    
    fun updatePosition() {
        service?.let {
            _currentPosition.value = it.getCurrentPosition()
            _duration.value = it.getDuration()
            _isPlaying.value = it.isPlaying()
            // 現在の曲IDも更新
            val newSongId = it.getCurrentSongId()
            if (newSongId != _currentSongId.value) {
                _currentSongId.value = newSongId
            }
        }
    }
    
    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        service?.applyAudioEffectSettings(settings)
    }
    
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        service?.setRepeatMode(mode)
    }
    
    fun toggleShuffle() {
        val newMode = if (_repeatMode.value == RepeatMode.SHUFFLE) {
            RepeatMode.NONE
        } else {
            RepeatMode.SHUFFLE
        }
        setRepeatMode(newMode)
    }
    
    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.NONE
            RepeatMode.SHUFFLE -> RepeatMode.REPEAT_ALL // シャッフル中は全曲ループに
        }
        setRepeatMode(newMode)
    }
    
    fun skipNext() {
        service?.playNextSong()
        // currentSongIdはMusicPlayerServiceから更新される
    }
    
    fun skipPrevious() {
        service?.playPreviousSong()
        // currentSongIdはMusicPlayerServiceから更新される
    }
    
    fun getCurrentSongId(): Long? {
        return service?.getCurrentSongId()
    }
    
    fun getAudioEffectManager(): AudioEffectManager? {
        return service?.getAudioEffectManagerInstance()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
