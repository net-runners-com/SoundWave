package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.home.HomeViewModel

/**
 * Paging対応のSongsTab（大量データ用）
 * パフォーマンス最適化: 必要な分だけレンダリング
 */
@Composable
fun SongsTabPaged(
    viewModel: HomeViewModel,
    onSongSelected: (Long) -> Unit
) {
    val songsPaged = viewModel.songsPaged.collectAsLazyPagingItems()
    val isLoading by viewModel.isLoading.collectAsState()
    
    if (isLoading && songsPaged.itemCount == 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (songsPaged.itemCount == 0 && !isLoading) {
        EmptyState(
            message = "音楽ファイルが見つかりません",
            actionLabel = "再スキャン",
            onAction = { viewModel.scanMusicFiles() }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                count = songsPaged.itemCount,
                key = { index -> songsPaged[index]?.id ?: index }
            ) { index ->
                val song = songsPaged[index]
                song?.let {
                    ListItemCard(
                        title = it.title,
                        subtitle = "${it.artist} - ${it.album}",
                        imageUrl = it.albumArtPath,
                        onClick = { onSongSelected(it.id) }
                    )
                }
            }
            
            // ローディング状態
            if (songsPaged.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

