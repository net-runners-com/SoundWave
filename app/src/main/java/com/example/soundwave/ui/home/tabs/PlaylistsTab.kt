package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundwave.ui.components.CreatePlaylistDialog
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.home.HomeViewModel

@Composable
fun PlaylistsTab(
    viewModel: HomeViewModel,
    onPlaylistSelected: (Long) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // データをcollectAsStateで監視（既にキャッシュされているので再読み込みなし）
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 検索フィルタリング
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) {
            playlists
        } else {
            playlists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // プレイリスト作成ダイアログ
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 検索バー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("プレイリストを検索") },
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
                    modifier = Modifier.weight(1f)
                )
            }
            
            // プレイリスト一覧
            if (isLoading && playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (playlists.isEmpty()) {
                EmptyState(message = "プレイリストがありません")
            } else if (filteredPlaylists.isEmpty()) {
                EmptyState(message = "検索結果が見つかりませんでした")
            } else {
                // LazyColumnの状態を保持してスクロール位置を維持
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        ListItemCard(
                            title = playlist.name,
                            content = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .padding(end = 12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = { onPlaylistSelected(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

