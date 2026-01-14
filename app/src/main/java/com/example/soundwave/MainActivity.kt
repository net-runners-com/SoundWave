package com.example.soundwave

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.soundwave.ui.navigation.SoundWaveNavigation
import com.example.soundwave.ui.theme.SoundWaveTheme
import com.example.soundwave.ui.theme.ThemeManager
import com.example.soundwave.ui.theme.AppTheme
import com.example.soundwave.ui.settings.LanguageManager
import com.example.soundwave.ui.settings.AppSettingsManager
import com.example.soundwave.player.PlayerManager
import java.util.Locale

class MainActivity : ComponentActivity() {
    
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            // アプリがバックグラウンドに移ったとき
            if (!AppSettingsManager.isBackgroundPlaybackEnabled(this@MainActivity)) {
                // バックグラウンド再生が無効な場合は音楽を一時停止
                PlayerManager.getInstance(this@MainActivity).pause()
            }
        }
        
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            // アプリがフォアグラウンドに戻ったときは何もしない（ユーザーが手動で再生する）
        }
    }
    override fun attachBaseContext(newBase: Context) {
        val selectedLanguage = LanguageManager.getSelectedLanguage(newBase)
        super.attachBaseContext(updateBaseContextLocale(newBase, selectedLanguage))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // アプリ全体のライフサイクルを監視
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        
        enableEdgeToEdge()
        setContent {
            var currentTheme by remember { mutableStateOf(ThemeManager(this).getSelectedTheme()) }
            
            SoundWaveTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SoundWaveNavigation(
                        onThemeChanged = { newTheme ->
                            currentTheme = newTheme
                        }
                    )
                }
            }
        }
    }
    
    private fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "ja" -> Locale.JAPANESE
            "en" -> Locale.ENGLISH
            "ko" -> Locale.KOREAN
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.JAPANESE
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ライフサイクルオブザーバーを削除
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }
}
