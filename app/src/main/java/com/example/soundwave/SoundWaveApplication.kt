package com.example.soundwave

import android.app.Application
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.repository.MusicRepository
import com.yausername.youtubedl_android.YoutubeDL
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
        
        // YouTubeDLの初期化（バックグラウンドで実行）
        applicationScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@SoundWaveApplication)
                android.util.Log.d("SoundWaveApplication", "YoutubeDL initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("SoundWaveApplication", "Failed to initialize YoutubeDL", e)
                // 初期化失敗は後で再試行可能
            }
        }
    }
}

