@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.util.Constants
import com.example.soundwave.util.TimeFormatter
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    songId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(context.applicationContext as android.app.Application)
    )
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    
    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }
    
    // 位置更新
    LaunchedEffect(Unit) {
        val playerManager = PlayerManager.getInstance(context)
        while (true) {
            playerManager.updatePosition()
            delay(Constants.POSITION_UPDATE_INTERVAL_MS)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("再生中") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // アルバムアート
            AlbumArtSection(song = currentSong)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 曲情報
            SongInfoSection(song = currentSong)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // シークバー
            SeekBarSection(
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { viewModel.seekTo(it.toLong()) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 再生コントロール
            PlaybackControlsSection(
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playPause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // その他のコントロール
            AdditionalControlsSection()
        }
    }
}

@Composable
private fun AlbumArtSection(song: com.example.soundwave.data.database.SongEntity?) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // TODO: Coilでアルバムアートを表示
        Icon(
            Icons.Default.Album,
            contentDescription = "アルバムアート",
            modifier = Modifier.size(200.dp)
        )
    }
}

@Composable
private fun SongInfoSection(song: com.example.soundwave.data.database.SongEntity?) {
    song?.let {
        Text(
            text = it.title,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${it.artist} - ${it.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeekBarSection(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Slider(
        value = currentPosition.toFloat(),
        onValueChange = onSeek,
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        modifier = Modifier.fillMaxWidth()
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = TimeFormatter.format(currentPosition),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = TimeFormatter.format(duration),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "前の曲")
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        FloatingActionButton(onClick = onPlayPause) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生"
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        IconButton(onClick = onSkipNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "次の曲")
        }
    }
}

@Composable
private fun AdditionalControlsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = { /* TODO: シャッフル */ }) {
            Icon(Icons.Default.Shuffle, contentDescription = "シャッフル")
        }
        IconButton(onClick = { /* TODO: ループ */ }) {
            Icon(Icons.Default.Repeat, contentDescription = "ループ")
        }
        IconButton(onClick = { /* TODO: 歌詞 */ }) {
            Icon(Icons.Default.TextFields, contentDescription = "歌詞")
        }
        IconButton(onClick = { /* TODO: エフェクト */ }) {
            Icon(Icons.Default.GraphicEq, contentDescription = "エフェクト")
        }
    }
}
