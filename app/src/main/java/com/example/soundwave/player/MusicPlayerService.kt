package com.example.soundwave.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
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
    private var mediaSession: MediaSession? = null
    private val binder = MusicBinder()
    
    private var currentSongId: Long? = null
    private var repeatMode: RepeatMode = RepeatMode.NONE
    private var currentSongTitle: String? = null
    private var currentSongArtist: String? = null
    private var currentAlbumArtPath: String? = null
    
    private val audioEffectManager by lazy {
        AudioEffectManager(applicationContext)
    }
    
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
        initializeMediaSession()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun initializePlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(applicationContext)
                .setHandleAudioBecomingNoisy(true) // ヘッドフォンが外れたときに自動停止
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK) // ネットワーク再生時のウェイクロック
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    updateNotification()
                                    updateWidget()
                                }
                                Player.STATE_ENDED -> {
                                    handlePlaybackEnded()
                                }
                            }
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            android.util.Log.e("MusicPlayerService", "Playback error: ${error.message}", error)
                            // エラーが発生した場合の処理
                            error.cause?.let {
                                android.util.Log.e("MusicPlayerService", "Error cause: ${it.message}", it)
                            }
                        }
                    })
                }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Failed to initialize ExoPlayer", e)
        }
    }
    
    private fun initializeMediaSession() {
        exoPlayer?.let { player ->
            try {
                mediaSession = MediaSession.Builder(this, player).build()
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Failed to initialize MediaSession", e)
            }
        }
    }
    
    fun playSong(filePath: String, songId: Long) {
        if (exoPlayer == null) {
            android.util.Log.w("MusicPlayerService", "ExoPlayer is null, initializing...")
            initializePlayer()
            initializeMediaSession()
        }
        
        exoPlayer?.let { player ->
            try {
                val uri = android.net.Uri.parse("file://$filePath")
                android.util.Log.d("MusicPlayerService", "Playing song: $filePath")
                
                currentSongId = songId
                
                // 曲情報を取得してから再生
                serviceScope.launch {
                    try {
                        val song = musicRepository.getSongById(songId)
                        song?.let {
                            currentSongTitle = it.title
                            currentSongArtist = it.artist
                            currentAlbumArtPath = it.albumArtPath
                            
                            // MediaItemにメタデータを設定
                            val artworkUri = if (it.albumArtPath.isNullOrBlank()) {
                                null
                            } else {
                                try {
                                    // file://スキームが既にある場合はそのまま、ない場合は追加
                                    if (it.albumArtPath.startsWith("file://") || it.albumArtPath.startsWith("content://")) {
                                        Uri.parse(it.albumArtPath)
                                    } else {
                                        Uri.parse("file://${it.albumArtPath}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("MusicPlayerService", "Failed to parse album art URI: ${it.albumArtPath}", e)
                                    null
                                }
                            }
                            
                            val mediaItem = MediaItem.Builder()
                                .setUri(uri)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(it.title)
                                        .setArtist(it.artist)
                                        .setAlbumTitle(it.album)
                                        .setArtworkUri(artworkUri)
                                        .build()
                                )
                                .build()
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                            
                            // エフェクトを適用
                            val audioSessionId = player.audioSessionId
                            if (audioSessionId != 0) {
                                audioEffectManager.attachToAudioSession(audioSessionId)
                                loadAndApplyAudioEffects()
                            }
                            
                            // 通知とウィジェットを更新
                            updateNotification()
                            updateWidget()
                        } ?: run {
                            // 曲情報が取得できない場合はURIのみで再生
                            val mediaItem = MediaItem.fromUri(uri)
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                            
                            // エフェクトを適用
                            val audioSessionId = player.audioSessionId
                            if (audioSessionId != 0) {
                                audioEffectManager.attachToAudioSession(audioSessionId)
                                loadAndApplyAudioEffects()
                            }
                            
                            // 通知とウィジェットを更新
                            updateNotification()
                            updateWidget()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerService", "Error getting song info", e)
                        // エラー時はURIのみで再生
                        val mediaItem = MediaItem.fromUri(uri)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                        
                        // エフェクトを適用
                        val audioSessionId = player.audioSessionId
                        if (audioSessionId != 0) {
                            audioEffectManager.attachToAudioSession(audioSessionId)
                            loadAndApplyAudioEffects()
                        }
                        
                        // 通知とウィジェットを更新
                        updateNotification()
                        updateWidget()
                    }
                }
                
                // 現在のrepeatModeを適用
                setRepeatMode(repeatMode)
                
                // 初期通知を表示（曲情報は後で更新される）
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
        updateWidget()
    }
    
    fun resume() {
        exoPlayer?.play()
        updateNotification()
        updateWidget()
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
    
    fun getAudioEffectManagerInstance(): AudioEffectManager {
        return audioEffectManager
    }
    
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
            ).apply {
                description = "音楽再生の通知"
                setShowBadge(false)
            }
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
        
        // アルバムアートを取得
        val albumArtBitmap = loadAlbumArtBitmap()
        
        // 前の曲アクション
        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "前の曲",
            createPreviousPendingIntent()
        )
        
        // 再生/一時停止アクション
        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "一時停止",
                createPlayPausePendingIntent()
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "再生",
                createPlayPausePendingIntent()
            )
        }
        
        // 次の曲アクション
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "次の曲",
            createNextPendingIntent()
        )
        
        val mediaSession = this.mediaSession
        val notificationBuilder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // ロック画面で表示
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setContentTitle(currentSongTitle ?: "SoundWave")
            .setContentText(currentSongArtist ?: "音楽を再生中")
            .setLargeIcon(albumArtBitmap)
            .addAction(previousAction) // #0
            .addAction(playPauseAction) // #1
            .addAction(nextAction) // #2
        
        // MediaStyleを適用
        if (mediaSession != null) {
            notificationBuilder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(1) // コンパクトビューに再生/一時停止ボタンを表示
            )
        } else {
            // MediaSessionがない場合は従来のMediaStyleを使用
            notificationBuilder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(1)
            )
        }
        
        return notificationBuilder.build()
    }
    
    private fun loadAlbumArtBitmap(): Bitmap? {
        return try {
            if (currentAlbumArtPath.isNullOrBlank()) {
                null
            } else {
                // メインスレッドでない場合はnullを返す（非同期で読み込む）
                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    // バックグラウンドスレッドの場合は、同期的に読み込む（通知作成時のみ）
                    try {
                        val imageLoader = ImageLoader(applicationContext)
                        val request = ImageRequest.Builder(applicationContext)
                            .data(currentAlbumArtPath)
                            .build()
                        val result = runBlocking(Dispatchers.IO) { imageLoader.execute(request) }
                        if (result is SuccessResult) {
                            val drawable = result.drawable
                            if (drawable is BitmapDrawable) {
                                drawable.bitmap
                            } else {
                                // BitmapDrawableでない場合は変換
                                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(canvas)
                                bitmap
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerService", "Error loading album art", e)
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Error loading album art", e)
            null
        }
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
    
    private fun createPreviousPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = Constants.ACTION_PREVIOUS
        }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNextPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = Constants.ACTION_NEXT
        }
        return PendingIntent.getService(
            this, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
    }
    
    private fun updateWidget() {
        // SharedPreferencesに現在の曲情報を保存
        val prefs = getSharedPreferences(Constants.PREFS_WIDGET, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(Constants.KEY_WIDGET_SONG_TITLE, currentSongTitle)
            putString(Constants.KEY_WIDGET_SONG_ARTIST, currentSongArtist)
            putString(Constants.KEY_WIDGET_ALBUM_ART_PATH, currentAlbumArtPath)
            putBoolean(Constants.KEY_WIDGET_IS_PLAYING, isPlaying())
            apply()
        }
        
        // ウィジェットを更新
        com.example.soundwave.widget.MusicPlayerWidget.updateAllWidgets(
            applicationContext,
            currentSongTitle,
            currentSongArtist,
            currentAlbumArtPath,
            isPlaying()
        )
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
            Constants.ACTION_PREVIOUS -> {
                playPreviousSong()
            }
            Constants.ACTION_NEXT -> {
                playNextSong()
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
        mediaSession?.release()
        mediaSession = null
        audioEffectManager.release()
        serviceScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    private fun loadAndApplyAudioEffects() {
        val settings = AudioEffectSettingsManager.loadSettings(applicationContext)
        audioEffectManager.applySettings(settings)
        
        // 再生速度とピッチを適用
        exoPlayer?.let { player ->
            try {
                val playbackParameters = PlaybackParameters(
                    settings.playbackSpeed,
                    settings.pitch
                )
                player.playbackParameters = playbackParameters
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Failed to apply playback parameters", e)
            }
        }
    }
    
    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        audioEffectManager.applySettings(settings)
        
        // 再生速度とピッチを適用
        exoPlayer?.let { player ->
            try {
                // ExoPlayerのPlaybackParametersを使用して速度とピッチを設定
                val playbackParameters = PlaybackParameters(
                    settings.playbackSpeed,
                    settings.pitch
                )
                player.playbackParameters = playbackParameters
                android.util.Log.d("MusicPlayerService", "Playback parameters applied: speed=${settings.playbackSpeed}, pitch=${settings.pitch}")
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Failed to apply playback parameters", e)
            }
        }
    }
    
}
