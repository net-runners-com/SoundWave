package com.example.soundwave.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.soundwave.MainActivity
import com.example.soundwave.R
import com.example.soundwave.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MusicPlayerWidget : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // SharedPreferencesから現在の曲情報を取得
        val prefs = context.getSharedPreferences(Constants.PREFS_WIDGET, Context.MODE_PRIVATE)
        val songTitle = prefs.getString(Constants.KEY_WIDGET_SONG_TITLE, null)
        val songArtist = prefs.getString(Constants.KEY_WIDGET_SONG_ARTIST, null)
        val albumArtPath = prefs.getString(Constants.KEY_WIDGET_ALBUM_ART_PATH, null)
        val isPlaying = prefs.getBoolean(Constants.KEY_WIDGET_IS_PLAYING, false)
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, songTitle, songArtist, albumArtPath, isPlaying)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            Constants.ACTION_PLAY_PAUSE,
            Constants.ACTION_PREVIOUS,
            Constants.ACTION_NEXT -> {
                // サービスにアクションを送信
                val serviceIntent = Intent(context, com.example.soundwave.player.MusicPlayerService::class.java).apply {
                    action = intent.action
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
    
    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            songTitle: String?,
            songArtist: String?,
            albumArtPath: String?,
            isPlaying: Boolean
        ) {
            widgetScope.launch {
                try {
                    val views = createRemoteViews(context, songTitle, songArtist, albumArtPath, isPlaying)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    android.util.Log.e("MusicPlayerWidget", "Error updating widget", e)
                    // エラー時はデフォルト表示
                    val views = createRemoteViews(context, null, null, null, false)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
        
        private fun createRemoteViews(
            context: Context,
            songTitle: String?,
            songArtist: String?,
            albumArtPath: String?,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)
            
            // 曲情報を設定
            if (!songTitle.isNullOrBlank()) {
                views.setTextViewText(R.id.widget_song_title, songTitle)
                views.setTextViewText(R.id.widget_song_artist, songArtist ?: "")
                
                // アルバムアートを読み込んで設定
                if (!albumArtPath.isNullOrBlank()) {
                    try {
                        val imageLoader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(albumArtPath)
                            .build()
                        val result = runBlocking(Dispatchers.IO) { imageLoader.execute(request) }
                        if (result is SuccessResult) {
                            val drawable = result.drawable
                            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                                drawable.bitmap
                            } else {
                                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(canvas)
                                bmp
                            }
                            views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerWidget", "Error loading album art", e)
                    }
                }
            } else {
                views.setTextViewText(R.id.widget_song_title, "曲が選択されていません")
                views.setTextViewText(R.id.widget_song_artist, "")
            }
            
            // 再生/一時停止ボタン
            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)
            
            // ボタンのクリックイベントを設定
            val playPauseIntent = Intent(context, MusicPlayerWidget::class.java).apply {
                action = Constants.ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)
            
            val previousIntent = Intent(context, MusicPlayerWidget::class.java).apply {
                action = Constants.ACTION_PREVIOUS
            }
            val previousPendingIntent = PendingIntent.getBroadcast(
                context, 1, previousIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_previous, previousPendingIntent)
            
            val nextIntent = Intent(context, MusicPlayerWidget::class.java).apply {
                action = Constants.ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
            
            // ウィジェット全体をクリックしてアプリを開く
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)
            
            return views
        }
        
        fun updateAllWidgets(
            context: Context,
            songTitle: String?,
            songArtist: String?,
            albumArtPath: String?,
            isPlaying: Boolean
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicPlayerWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, songTitle, songArtist, albumArtPath, isPlaying)
            }
        }
    }
}
