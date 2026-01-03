package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
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
    var searchQuery by remember { mutableStateOf("") }
    
    // データをcollectAsStateで監視（既にキャッシュされているので再読み込みなし）
    val songs by viewModel.songs.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 検索フィルタリング
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 検索バー
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("曲を検索") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "クリア")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        
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
        } else if (filteredSongs.isEmpty()) {
            EmptyState(message = "検索結果が見つかりませんでした")
        } else {
            // LazyColumnの状態を保持してスクロール位置を維持
            val listState = rememberLazyListState()
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
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
}

