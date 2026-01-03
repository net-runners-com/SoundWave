package com.example.soundwave.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.database.SongEntity
import com.example.soundwave.player.PlayerManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun NowPlayingFAB(
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onExpandClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val playerManager = remember { PlayerManager.getInstance(context) }
    
    var currentSong by remember { mutableStateOf<SongEntity?>(null) }
    val scope = rememberCoroutineScope()
    
    // 曲IDが変わったときに曲情報を取得
    LaunchedEffect(currentSongId) {
        if (currentSongId != null) {
            scope.launch {
                currentSong = musicRepository.getSongById(currentSongId)
            }
        } else {
            currentSong = null
        }
    }
    
    // 曲が再生中の場合のみ表示
    if (currentSong != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 上矢印ボタン（ボトムシートを開く）
                IconButton(
                    onClick = onExpandClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "展開",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // アルバムアート
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentSong!!.albumArtPath)
                        .crossfade(true)
                        .placeholder(android.R.drawable.ic_media_play)
                        .error(android.R.drawable.ic_media_play)
                        .build(),
                    contentDescription = currentSong!!.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 曲情報
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = currentSong!!.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSong!!.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 再生/一時停止ボタン
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "一時停止" else "再生",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

