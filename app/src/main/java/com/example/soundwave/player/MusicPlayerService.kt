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
import com.example.soundwave.util.Constants

class MusicPlayerService : Service() {
    private var exoPlayer: ExoPlayer? = null
    private val binder = MusicBinder()
    
    private var currentSongId: Long? = null
    
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
                                // TODO: 次の曲を再生
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
        exoPlayer?.let { player ->
            val uri = android.net.Uri.parse("file://$filePath")
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            currentSongId = songId
            startForeground(Constants.NOTIFICATION_ID, createNotification())
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
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
    
}

