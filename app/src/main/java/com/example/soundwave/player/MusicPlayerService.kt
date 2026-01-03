package com.example.soundwave.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import com.example.soundwave.MainActivity
import com.example.soundwave.R
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicPlayerService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private val binder = MusicBinder()
    
    private var currentSongId: Long? = null
    private var repeatMode: RepeatMode = RepeatMode.NONE
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicRepository by lazy {
        AppDatabaseModule.getMusicRepository(applicationContext)
    }
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun initializePlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                updateNotification()
                            }
                            Player.STATE_ENDED -> {
                                handlePlaybackEnded()
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Failed to initialize ExoPlayer", e)
        }
    }
    
    fun playSong(filePath: String, songId: Long) {
        if (exoPlayer == null) {
            android.util.Log.w("MusicPlayerService", "ExoPlayer is null, initializing...")
            initializePlayer()
        }
        
        exoPlayer?.let { player ->
            try {
                val uri = android.net.Uri.parse("file://$filePath")
                android.util.Log.d("MusicPlayerService", "Playing song: $filePath")
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                currentSongId = songId
                // 現在のrepeatModeを適用
                setRepeatMode(repeatMode)
                startForeground(Constants.NOTIFICATION_ID, createNotification())
                android.util.Log.d("MusicPlayerService", "Song playback started")
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Error playing song", e)
            }
        } ?: run {
            android.util.Log.e("MusicPlayerService", "ExoPlayer is still null after initialization")
        }
    }
    
    fun pause() {
        exoPlayer?.pause()
        updateNotification()
    }
    
    fun resume() {
        exoPlayer?.play()
        updateNotification()
    }
    
    fun stop() {
        exoPlayer?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }
    
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }
    
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    fun getCurrentSongId(): Long? = currentSongId
    
    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        // exoPlayerが初期化されている場合のみ設定を適用
        exoPlayer?.let { player ->
            try {
                when (mode) {
                    RepeatMode.NONE -> {
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        player.shuffleModeEnabled = false
                    }
                    RepeatMode.REPEAT_ONE -> {
                        player.repeatMode = Player.REPEAT_MODE_ONE
                        player.shuffleModeEnabled = false
                    }
                RepeatMode.REPEAT_ALL -> {
                    // 全曲ループ: ExoPlayerのREPEAT_MODE_OFFにして、STATE_ENDEDで次の曲に進む
                    // 単一のMediaItemしか設定されていないため、REPEAT_MODE_ALLは機能しない
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.shuffleModeEnabled = false
                }
                    RepeatMode.SHUFFLE -> {
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        player.shuffleModeEnabled = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Error setting repeat mode", e)
            }
        }
    }
    
    private fun handlePlaybackEnded() {
        exoPlayer?.let { player ->
            when (repeatMode) {
                RepeatMode.REPEAT_ONE -> {
                    // 1曲ループ: 同じ曲を最初から再生
                    // ExoPlayerのREPEAT_MODE_ONEで自動的に処理されるため、ここでは何もしない
                    // ただし、念のためseekTo(0)を実行
                    try {
                        player.seekTo(0)
                        player.play()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerService", "Error in REPEAT_ONE handling", e)
                    }
                }
                RepeatMode.REPEAT_ALL -> {
                    // 全曲ループ: 次の曲を再生
                    playNextSong()
                }
                RepeatMode.SHUFFLE -> {
                    // シャッフル: ExoPlayerのshuffleModeEnabledで自動的に処理される
                    // 何もしない
                }
                RepeatMode.NONE -> {
                    // ループなし: 再生を停止
                    try {
                        pause()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerService", "Error in NONE handling", e)
                    }
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                R.drawable.ic_menu_camera, // TODO: 適切なアイコンに変更
                "Pause",
                createPlayPausePendingIntent()
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_menu_camera, // TODO: 適切なアイコンに変更
                "Play",
                createPlayPausePendingIntent()
            )
        }
        
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SoundWave")
            .setContentText("音楽を再生中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0))
            .build()
    }
    
    private fun createPlayPausePendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = Constants.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_PLAY_PAUSE -> {
                if (isPlaying()) {
                    pause()
                } else {
                    resume()
                }
            }
        }
        return START_STICKY
    }
    
    fun playNextSong() {
        serviceScope.launch {
            playNextSongInternal()
        }
    }
    
    fun playPreviousSong() {
        serviceScope.launch {
            playPreviousSongInternal()
        }
    }
    
    private suspend fun playNextSongInternal() {
        val currentId = currentSongId ?: return
        try {
            // すべての曲を取得
            val allSongs = musicRepository.getAllSongsSync()
            if (allSongs.isEmpty()) {
                android.util.Log.w("MusicPlayerService", "No songs available for next song")
                return
            }
            
            // 現在の曲のインデックスを探す
            val currentIndex = allSongs.indexOfFirst { song -> song.id == currentId }
            if (currentIndex == -1) {
                android.util.Log.w("MusicPlayerService", "Current song not found in list")
                return
            }
            
            // 次の曲のインデックスを計算（最後の曲の場合は最初に戻る）
            val nextIndex = (currentIndex + 1) % allSongs.size
            val nextSong = allSongs[nextIndex]
            
            android.util.Log.d("MusicPlayerService", "Playing next song: ${nextSong.title} (index: $nextIndex)")
            // 次の曲を再生
            playSong(nextSong.filePath, nextSong.id)
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Error playing next song", e)
        }
    }
    
    private suspend fun playPreviousSongInternal() {
        val currentId = currentSongId ?: return
        try {
            // すべての曲を取得
            val allSongs = musicRepository.getAllSongsSync()
            if (allSongs.isEmpty()) {
                android.util.Log.w("MusicPlayerService", "No songs available for previous song")
                return
            }
            
            // 現在の曲のインデックスを探す
            val currentIndex = allSongs.indexOfFirst { song -> song.id == currentId }
            if (currentIndex == -1) {
                android.util.Log.w("MusicPlayerService", "Current song not found in list")
                return
            }
            
            // 前の曲のインデックスを計算（最初の曲の場合は最後に戻る）
            val previousIndex = if (currentIndex == 0) {
                allSongs.size - 1
            } else {
                currentIndex - 1
            }
            val previousSong = allSongs[previousIndex]
            
            android.util.Log.d("MusicPlayerService", "Playing previous song: ${previousSong.title} (index: $previousIndex)")
            // 前の曲を再生
            playSong(previousSong.filePath, previousSong.id)
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Error playing previous song", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
    
}

