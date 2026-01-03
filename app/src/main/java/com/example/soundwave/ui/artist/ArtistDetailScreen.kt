@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.artist

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.TabItem
import com.example.soundwave.ui.components.NowPlayingFAB
import com.example.soundwave.ui.player.PlayerScreen
import com.example.soundwave.player.PlayerManager
import kotlinx.coroutines.launch

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onSongSelected: (Long) -> Unit = {},
    onAlbumSelected: (String) -> Unit = {},
    onArtistSelected: (String) -> Unit = {},
    onFolderSelected: (String) -> Unit = {},
    onPlaylistSelected: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val playerManager = remember { PlayerManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // 現在再生中の曲情報を取得
    val currentSongId by playerManager.currentSongId.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    
    // アーティストの曲を取得
    val songs by musicRepository.getSongsByArtist(artistName).collectAsState(initial = emptyList())
    
    var selectedBottomTabIndex by remember { mutableStateOf(TabItem.ARTISTS.ordinal) }
    
    // ボトムシートの状態
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // 再生中のミニプレーヤー
                NowPlayingFAB(
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onSongClick = {}, // 画面遷移しない
                    onPlayPause = {
                        if (isPlaying) {
                            playerManager.pause()
                        } else {
                            playerManager.resume()
                        }
                    },
                    onExpandClick = {
                        if (currentSongId != null) {
                            showBottomSheet = true
                        }
                    }
                )
                
                // ボトムタブ
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TabItem.values().forEachIndexed { index, tabItem ->
                            val isSelected = selectedBottomTabIndex == index
                            IconButton(
                                onClick = { 
                                    selectedBottomTabIndex = index
                                    // タブがクリックされたらHomeScreenに戻る
                                    if (index != TabItem.ARTISTS.ordinal) {
                                        onBack()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Icon(
                                        imageVector = tabItem.icon,
                                        contentDescription = tabItem.title,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .padding(top = 8.dp),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(message = "このアーティストの曲がありません")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs.sortedBy { it.album }, key = { it.id }) { song ->
                    ListItemCard(
                        title = song.title,
                        subtitle = "${song.album}",
                        imageUrl = song.albumArtPath,
                        onClick = { onSongSelected(song.id) }
                    )
                }
            }
        }
    }
    
    // ボトムシート（プレーヤー画面）
    if (showBottomSheet && currentSongId != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            modifier = Modifier.fillMaxHeight()
        ) {
            PlayerScreen(
                songId = currentSongId!!,
                onBack = {
                    scope.launch {
                        bottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }
            )
        }
    }
}

