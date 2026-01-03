package com.example.soundwave.ui.home.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.home.HomeViewModel
import java.io.File

@Composable
fun FoldersTab(
    viewModel: HomeViewModel,
    onFolderSelected: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val folders by viewModel.folders.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 検索フィルタリング
    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) {
            folders
        } else {
            folders.filter {
                val folderName = File(it).name.ifEmpty { it }
                folderName.contains(searchQuery, ignoreCase = true) ||
                it.contains(searchQuery, ignoreCase = true)
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
            label = { Text("フォルダを検索") },
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
        if (isLoading && folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (folders.isEmpty()) {
            EmptyState(message = "フォルダが見つかりません")
        } else if (filteredFolders.isEmpty()) {
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
                items(filteredFolders, key = { it }) { folderPath ->
                    val folderName = File(folderPath).name.ifEmpty { folderPath }
                    ListItemCard(
                        title = folderName,
                        subtitle = folderPath,
                        content = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = { onFolderSelected(folderPath) }
                    )
                }
            }
        }
    }
}



