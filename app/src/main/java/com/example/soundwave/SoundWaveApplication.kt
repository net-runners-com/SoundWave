package com.example.soundwave

import android.app.Application
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.repository.MusicRepository
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
    }
}

