package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.home.HomeViewModel

@Composable
fun SongsTab(
    viewModel: HomeViewModel,
    onSongSelected: (Long) -> Unit
) {
    // データをcollectAsStateで監視（既にキャッシュされているので再読み込みなし）
    val songs by viewModel.songs.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 初回のみローディング表示、データが既にある場合はスキップ
    if (isLoading && songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (songs.isEmpty()) {
        EmptyState(
            message = "音楽ファイルが見つかりません",
            actionLabel = "再スキャン",
            onAction = { viewModel.scanMusicFiles() }
        )
    } else {
        // LazyColumnの状態を保持してスクロール位置を維持
        val listState = rememberLazyListState()
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                ListItemCard(
                    title = song.title,
                    subtitle = "${song.artist} - ${song.album}",
                    imageUrl = song.albumArtPath,
                    onClick = { onSongSelected(song.id) }
                )
            }
        }
    }
}

