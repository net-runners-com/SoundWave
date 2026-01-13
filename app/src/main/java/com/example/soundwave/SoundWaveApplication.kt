package com.example.soundwave

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.service.LocationMonitoringService
import com.example.soundwave.ui.settings.LanguageManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * アプリケーションクラス
 * 起動時にデータを事前取得してパフォーマンスを最適化
 */
class SoundWaveApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        // アプリ起動時にデータを事前取得（バックグラウンドで実行）
        applicationScope.launch(Dispatchers.IO) {
            val musicRepository: MusicRepository = AppDatabaseModule.getMusicRepository(this@SoundWaveApplication)
            
            // データベースにデータがあるか確認
            val songs = musicRepository.getAllSongs().first()
            if (songs.isEmpty()) {
                // データがない場合はスキャンを実行（非同期でファイルアクセス）
                musicRepository.scanMusicFiles()
            }
        }
        
        // YouTubeDLとFFmpegの初期化（バックグラウンドで実行）
        // FFmpegは音声抽出（--extract-audio）などの機能に必要
        applicationScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@SoundWaveApplication)
                FFmpeg.getInstance().init(this@SoundWaveApplication)
                android.util.Log.d("SoundWaveApplication", "YoutubeDL and FFmpeg initialized successfully")
            } catch (e: YoutubeDLException) {
                android.util.Log.e("SoundWaveApplication", "failed to initialize youtubedl-android", e)
            }
        }
        
        // 位置情報監視サービスを起動（権限があれば）
        startLocationMonitoringServiceIfPermitted()
    }
    
    private fun startLocationMonitoringServiceIfPermitted() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasFineLocation || hasCoarseLocation) {
            try {
                val intent = Intent(this, LocationMonitoringService::class.java).apply {
                    action = LocationMonitoringService.ACTION_START_MONITORING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                android.util.Log.d("SoundWaveApplication", "Location monitoring service started")
            } catch (e: Exception) {
                android.util.Log.e("SoundWaveApplication", "Failed to start location monitoring service", e)
            }
        } else {
            android.util.Log.d("SoundWaveApplication", "Location permissions not granted, skipping service start")
        }
    }
}

