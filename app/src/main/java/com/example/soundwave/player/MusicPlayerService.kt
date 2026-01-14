package com.example.soundwave.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import android.os.Bundle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.pow

class MusicPlayerService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val binder = MusicBinder()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        android.util.Log.d("MusicPlayerService", "Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永続的なフォーカス損失（他アプリがオーディオを再生した場合）
                val shouldStop = com.example.soundwave.ui.settings.AppSettingsManager.isStopOnOtherAppEnabled(applicationContext)
                android.util.Log.d("MusicPlayerService", "AUDIOFOCUS_LOSS: shouldStop=$shouldStop")
                if (shouldStop) {
                    android.util.Log.d("MusicPlayerService", "Pausing playback due to audio focus loss")
                    pause()
                } else {
                    android.util.Log.d("MusicPlayerService", "Not pausing playback (setting is OFF)")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 一時的なフォーカス損失（他アプリが一時的にオーディオを再生した場合）
                val shouldStop = com.example.soundwave.ui.settings.AppSettingsManager.isStopOnOtherAppEnabled(applicationContext)
                android.util.Log.d("MusicPlayerService", "AUDIOFOCUS_LOSS_TRANSIENT: shouldStop=$shouldStop")
                if (shouldStop) {
                    android.util.Log.d("MusicPlayerService", "Pausing playback due to transient audio focus loss")
                    pause()
                } else {
                    android.util.Log.d("MusicPlayerService", "Not pausing playback (setting is OFF)")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 音量を下げる（通知音など）
                // 設定に関係なく、音量を下げる
                android.util.Log.d("MusicPlayerService", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: ducking volume")
                exoPlayer?.volume = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // オーディオフォーカスを再取得した場合
                android.util.Log.d("MusicPlayerService", "AUDIOFOCUS_GAIN: restoring volume")
                exoPlayer?.volume = 1.0f
            }
        }
    }
    
    private var currentSongId: Long? = null
    private var repeatMode: RepeatMode = RepeatMode.NONE
    private var currentSongTitle: String? = null
    private var currentSongArtist: String? = null
    private var currentAlbumArtPath: String? = null
    
    // コンテキストモード用の変数（アルバム、アーティスト、フォルダ、プレイリスト）
    private var currentPlaylistId: Long? = null
    private var currentAlbumName: String? = null
    private var currentArtistName: String? = null
    private var currentFolderPath: String? = null
    private var contextSongs: List<com.example.soundwave.data.database.SongEntity> = emptyList()
    
    private val audioEffectManager by lazy {
        AudioEffectManager(applicationContext)
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicRepository by lazy {
        AppDatabaseModule.getMusicRepository(applicationContext)
    }
    private val playlistRepository by lazy {
        AppDatabaseModule.getPlaylistRepository(applicationContext)
    }
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializePlayer()
        // こいつ
        initializeMediaSession()
        setupAudioFocus()
    }
    
    private fun setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
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
                                    // PlayerManagerの状態も更新（曲が切り替わった場合に備えて）
                                    try {
                                        val playerManager = PlayerManager.getInstance(applicationContext)
                                        playerManager.setCurrentSongId(currentSongId)
                                        playerManager.setPlaying(exoPlayer?.isPlaying ?: false)
                                    } catch (e: Exception) {
                                        android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state in STATE_READY", e)
                                    }
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
                mediaSession = MediaSession.Builder(this, player)
                    .setCallback(object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo
                        ): MediaSession.ConnectionResult {
                            // デフォルトのセッションコマンドとプレイヤーコマンドを使用
                            // ExoPlayerはデフォルトでCOMMAND_SKIP_TO_NEXTとCOMMAND_SKIP_TO_PREVIOUSをサポート
                            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                )
                                .setAvailablePlayerCommands(
                                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                                )
                                .build()
                        }
                        
                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle
                        ): ListenableFuture<SessionResult> {
                            return super.onCustomCommand(session, controller, customCommand, args)
                        }
                    })
                    .build()
                
                // ExoPlayerのリスナーで次/前の曲の操作をハンドル
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                            // 曲が変わった時の処理
                            val currentMediaItem = player.currentMediaItem
                            if (currentMediaItem != null) {
                                // MediaItemのmediaIdから曲IDを取得して更新
                                val mediaId = currentMediaItem.mediaId
                                if (mediaId != null) {
                                    try {
                                        val songId = mediaId.toLongOrNull()
                                        if (songId != null) {
                                            serviceScope.launch {
                                                try {
                                                    val song = withContext(Dispatchers.IO) {
                                                        musicRepository.getSongById(songId)
                                                    }
                                                    song?.let {
                                                        currentSongId = it.id
                                                        currentSongTitle = it.title
                                                        currentSongArtist = it.artist
                                                        currentAlbumArtPath = it.albumArtPath
                                                        
                                                        // PlayerManagerの状態を更新
                                                        try {
                                                            val playerManager = PlayerManager.getInstance(applicationContext)
                                                            playerManager.setCurrentSongId(it.id)
                                                            playerManager.setPlaying(player.isPlaying)
                                                        } catch (e: Exception) {
                                                            android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
                                                        }
                                                        
                                                        updateNotification()
                                                        updateWidget()
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("MusicPlayerService", "Error updating song info on transition", e)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MusicPlayerService", "Error parsing mediaId: $mediaId", e)
                                    }
                                }
                            }
                        }
                    }
                })
                
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
                            
                            // 現在のコンテキストに基づいて曲リストを取得
                            val songs: List<com.example.soundwave.data.database.SongEntity>
                            if (contextSongs.isNotEmpty()) {
                                songs = contextSongs
                            } else {
                                songs = withContext(Dispatchers.IO) {
                                    musicRepository.getAllSongsSync()
                                }
                            }
                            
                            // 現在の曲のインデックスを探す
                            val currentIndex = songs.indexOfFirst { song -> song.id == songId }
                            if (currentIndex == -1) {
                                android.util.Log.w("MusicPlayerService", "Current song not found in list, playing single item")
                                // 曲が見つからない場合は単一アイテムとして再生
                                val mediaItem = MediaItem.Builder()
                                    .setUri(uri)
                                    .setMediaId(songId.toString())
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(it.title)
                                            .setArtist(it.artist)
                                            .setAlbumTitle(it.album)
                                            .setArtworkUri(artworkUri)
                                            .build()
                                    )
                                    .build()
                                if (requestAudioFocus()) {
                                    player.setMediaItem(mediaItem)
                                    player.prepare()
                                    player.play()
                                }
                            } else {
                                // 複数の曲をキューに入れる
                                val mediaItems = songs.map { song ->
                                    val songUri = android.net.Uri.parse("file://${song.filePath}")
                                    val songArtworkUri = if (song.albumArtPath.isNullOrBlank()) {
                                        null
                                    } else {
                                        try {
                                            if (song.albumArtPath.startsWith("file://") || song.albumArtPath.startsWith("content://")) {
                                                Uri.parse(song.albumArtPath)
                                            } else {
                                                Uri.parse("file://${song.albumArtPath}")
                                            }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    
                                    MediaItem.Builder()
                                        .setUri(songUri)
                                        .setMediaId(song.id.toString())
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(song.title)
                                                .setArtist(song.artist)
                                                .setAlbumTitle(song.album)
                                                .setArtworkUri(songArtworkUri)
                                                .build()
                                        )
                                        .build()
                                }
                                
                                // オーディオフォーカスをリクエスト
                                if (requestAudioFocus()) {
                                    player.setMediaItems(mediaItems, currentIndex, 0)
                                    player.prepare()
                                    player.play()
                                } else {
                                    android.util.Log.w("MusicPlayerService", "Failed to gain audio focus")
                                }
                            }
                            
                            // PlayerManagerの状態を更新
                            try {
                                val playerManager = PlayerManager.getInstance(applicationContext)
                                playerManager.setCurrentSongId(songId)
                                playerManager.setPlaying(true)
                            } catch (e: Exception) {
                                android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
                            }
                            
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
                            // オーディオフォーカスをリクエスト
                            if (requestAudioFocus()) {
                                val mediaItem = MediaItem.Builder()
                                    .setUri(uri)
                                    .setMediaId(songId.toString())
                                    .build()
                                player.setMediaItem(mediaItem)
                                player.prepare()
                                player.play()
                            } else {
                                android.util.Log.w("MusicPlayerService", "Failed to gain audio focus")
                            }
                            
                            // PlayerManagerの状態を更新
                            try {
                                val playerManager = PlayerManager.getInstance(applicationContext)
                                playerManager.setCurrentSongId(songId)
                                playerManager.setPlaying(true)
                            } catch (e: Exception) {
                                android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
                            }
                            
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
                        // オーディオフォーカスをリクエスト
                        if (requestAudioFocus()) {
                            val mediaItem = MediaItem.Builder()
                                .setUri(uri)
                                .setMediaId(songId.toString())
                                .build()
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                        } else {
                            android.util.Log.w("MusicPlayerService", "Failed to gain audio focus")
                        }
                        
                        // PlayerManagerの状態を更新
                        try {
                            val playerManager = PlayerManager.getInstance(applicationContext)
                            playerManager.setCurrentSongId(songId)
                            playerManager.setPlaying(true)
                        } catch (e: Exception) {
                            android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
                        }
                        
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
        // PlayerManagerの状態を更新
        try {
            val playerManager = PlayerManager.getInstance(applicationContext)
            playerManager.setPlaying(false)
        } catch (e: Exception) {
            android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
        }
        updateNotification()
        updateWidget()
    }
    
    fun resume() {
        exoPlayer?.play()
        // PlayerManagerの状態を更新
        try {
            val playerManager = PlayerManager.getInstance(applicationContext)
            playerManager.setPlaying(true)
        } catch (e: Exception) {
            android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
        }
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
                    // ループなし: 先頭に戻して停止
                    try {
                        player.seekTo(0)
                        pause()
                        android.util.Log.d("MusicPlayerService", "Song ended, reset to beginning and paused")
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
                    .setShowActionsInCompactView(0, 1, 2) // コンパクトビューに前の曲、再生/一時停止、次の曲ボタンを表示
            )
        } else {
            // MediaSessionがない場合は従来のMediaStyleを使用
            notificationBuilder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2) // コンパクトビューに前の曲、再生/一時停止、次の曲ボタンを表示
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
            val songs: List<com.example.soundwave.data.database.SongEntity>
            
            // コンテキストモード（アルバム、アーティスト、フォルダ、プレイリスト）の場合はそのコンテキスト内の曲を使用
            if (contextSongs.isNotEmpty()) {
                songs = contextSongs
                android.util.Log.d("MusicPlayerService", "Playing next song from context (playlist: $currentPlaylistId, album: $currentAlbumName, artist: $currentArtistName, folder: $currentFolderPath)")
            } else {
                // 通常モード: すべての曲を取得
                songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsSync()
                }
            }
            
            if (songs.isEmpty()) {
                android.util.Log.w("MusicPlayerService", "No songs available for next song")
                return
            }
            
            // 現在の曲のインデックスを探す
            val currentIndex = songs.indexOfFirst { song -> song.id == currentId }
            if (currentIndex == -1) {
                android.util.Log.w("MusicPlayerService", "Current song not found in list")
                return
            }
            
            // 次の曲のインデックスを計算（最後の曲の場合は最初に戻る）
            val nextIndex = (currentIndex + 1) % songs.size
            val nextSong = songs[nextIndex]
            
            android.util.Log.d("MusicPlayerService", "Playing next song: ${nextSong.title} (index: $nextIndex)")
            // 次の曲を再生（メインスレッドで実行）
            withContext(Dispatchers.Main) {
                playSong(nextSong.filePath, nextSong.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Error playing next song", e)
        }
    }
    
    private suspend fun playPreviousSongInternal() {
        val currentId = currentSongId ?: return
        try {
            val songs: List<com.example.soundwave.data.database.SongEntity>
            
            // コンテキストモード（アルバム、アーティスト、フォルダ、プレイリスト）の場合はそのコンテキスト内の曲を使用
            if (contextSongs.isNotEmpty()) {
                songs = contextSongs
                android.util.Log.d("MusicPlayerService", "Playing previous song from context (playlist: $currentPlaylistId, album: $currentAlbumName, artist: $currentArtistName, folder: $currentFolderPath)")
            } else {
                // 通常モード: すべての曲を取得
                songs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsSync()
                }
            }
            
            if (songs.isEmpty()) {
                android.util.Log.w("MusicPlayerService", "No songs available for previous song")
                return
            }
            
            // 現在の曲のインデックスを探す
            val currentIndex = songs.indexOfFirst { song -> song.id == currentId }
            if (currentIndex == -1) {
                android.util.Log.w("MusicPlayerService", "Current song not found in list")
                return
            }
            
            // 前の曲のインデックスを計算（最初の曲の場合は最後に戻る）
            val previousIndex = if (currentIndex == 0) {
                songs.size - 1
            } else {
                currentIndex - 1
            }
            val previousSong = songs[previousIndex]
            
            android.util.Log.d("MusicPlayerService", "Playing previous song: ${previousSong.title} (index: $previousIndex)")
            // 前の曲を再生（メインスレッドで実行）
            withContext(Dispatchers.Main) {
                playSong(previousSong.filePath, previousSong.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerService", "Error playing previous song", e)
        }
    }
    
    // プレイリストを再生するメソッド（外部から呼び出し可能）
    fun playPlaylist(playlistId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // プレイリスト内の曲を取得
                val songs = playlistRepository.getSongsInPlaylist(playlistId).first()
                
                if (songs.isNotEmpty()) {
                    // コンテキストモードを設定
                    currentPlaylistId = playlistId
                    currentAlbumName = null
                    currentArtistName = null
                    currentFolderPath = null
                    contextSongs = songs
                    
                    // 最初の曲を再生（メインスレッドで実行）
                    val firstSong = songs[0]
                    android.util.Log.d("MusicPlayerService", "Playing playlist: $playlistId, first song: ${firstSong.title}")
                    withContext(Dispatchers.Main) {
                        // playSongを呼び出す前にcurrentSongIdを設定
                        currentSongId = firstSong.id
                        // PlayerManagerの状態も即座に更新してUIに反映
                        try {
                            val playerManager = PlayerManager.getInstance(applicationContext)
                            playerManager.setCurrentSongId(firstSong.id)
                            playerManager.setPlaying(true)
                        } catch (e: Exception) {
                            android.util.Log.w("MusicPlayerService", "Could not update PlayerManager state", e)
                        }
                        playSong(firstSong.filePath, firstSong.id)
                    }
                } else {
                    android.util.Log.w("MusicPlayerService", "Playlist is empty: $playlistId")
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Error playing playlist", e)
            }
        }
    }
    
    // コンテキストモードを解除するメソッド
    fun clearContextMode() {
        currentPlaylistId = null
        currentAlbumName = null
        currentArtistName = null
        currentFolderPath = null
        contextSongs = emptyList()
        android.util.Log.d("MusicPlayerService", "Context mode cleared")
    }
    
    // アルバムコンテキストを設定するメソッド
    fun setAlbumContext(albumName: String, songs: List<com.example.soundwave.data.database.SongEntity>) {
        currentPlaylistId = null
        currentAlbumName = albumName
        currentArtistName = null
        currentFolderPath = null
        contextSongs = songs
        android.util.Log.d("MusicPlayerService", "Album context set: $albumName (${songs.size} songs)")
    }
    
    // アーティストコンテキストを設定するメソッド
    fun setArtistContext(artistName: String, songs: List<com.example.soundwave.data.database.SongEntity>) {
        currentPlaylistId = null
        currentAlbumName = null
        currentArtistName = artistName
        currentFolderPath = null
        contextSongs = songs
        android.util.Log.d("MusicPlayerService", "Artist context set: $artistName (${songs.size} songs)")
    }
    
    // フォルダコンテキストを設定するメソッド
    fun setFolderContext(folderPath: String, songs: List<com.example.soundwave.data.database.SongEntity>) {
        currentPlaylistId = null
        currentAlbumName = null
        currentArtistName = null
        currentFolderPath = folderPath
        contextSongs = songs
        android.util.Log.d("MusicPlayerService", "Folder context set: $folderPath (${songs.size} songs)")
    }
    
    // プレイリストコンテキストを設定するメソッド
    fun setPlaylistContext(playlistId: Long, songs: List<com.example.soundwave.data.database.SongEntity>) {
        currentPlaylistId = playlistId
        currentAlbumName = null
        currentArtistName = null
        currentFolderPath = null
        contextSongs = songs
        android.util.Log.d("MusicPlayerService", "Playlist context set: $playlistId (${songs.size} songs)")
    }
    
    // プレイリストモードを解除するメソッド（後方互換性のため残す）
    fun clearPlaylistMode() {
        clearContextMode()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
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
        
        // 再生速度とピッチを適用（キーシフトを考慮）
        exoPlayer?.let { player ->
            try {
                // キーシフトをピッチに変換（セミトーン単位: 2^(keyShift/12)）
                val keyShiftPitch = 2.0.pow(settings.keyShift / 12.0).toFloat()
                val finalPitch = settings.pitch * keyShiftPitch
                
                val playbackParameters = PlaybackParameters(
                    settings.playbackSpeed,
                    finalPitch
                )
                player.playbackParameters = playbackParameters
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Failed to apply playback parameters", e)
            }
        }
    }
    
    fun applyAudioEffectSettings(settings: AudioEffectSettings) {
        audioEffectManager.applySettings(settings)
        
        // 再生速度とピッチを適用（キーシフトを考慮）
        exoPlayer?.let { player ->
            try {
                // キーシフトをピッチに変換（セミトーン単位: 2^(keyShift/12)）
                val keyShiftPitch = 2.0.pow(settings.keyShift / 12.0).toFloat()
                val finalPitch = settings.pitch * keyShiftPitch
                
                // ExoPlayerのPlaybackParametersを使用して速度とピッチを設定
                val playbackParameters = PlaybackParameters(
                    settings.playbackSpeed,
                    finalPitch
                )
                player.playbackParameters = playbackParameters
                android.util.Log.d("MusicPlayerService", "Playback parameters applied: speed=${settings.playbackSpeed}, pitch=${settings.pitch}, keyShift=${settings.keyShift}, finalPitch=$finalPitch")
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerService", "Failed to apply playback parameters", e)
            }
        }
    }
    
}
