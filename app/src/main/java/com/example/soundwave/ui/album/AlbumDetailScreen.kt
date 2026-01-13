@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.soundwave.ui.album

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.ui.components.ListItemCard
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.TabItem
import com.example.soundwave.ui.components.NowPlayingFAB
import com.example.soundwave.ui.components.CreatePlaylistDialog
import com.example.soundwave.ui.home.tabs.PlaylistOptionsMenu
import com.example.soundwave.ui.home.tabs.SelectPlaylistDialog
import com.example.soundwave.ui.player.PlayerScreen
import com.example.soundwave.player.PlayerManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@Composable
fun AlbumDetailScreen(
    albumName: String,
    onBack: () -> Unit,
    onSongSelected: (Long) -> Unit = {},
    onAlbumSelected: (String) -> Unit = {},
    onArtistSelected: (String) -> Unit = {},
    onFolderSelected: (String) -> Unit = {},
    onPlaylistSelected: (Long) -> Unit = {},
    onSongDetail: (Long) -> Unit = {},
    onEditLyrics: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val musicRepository = remember { AppDatabaseModule.getMusicRepository(context) }
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    val playerManager = remember { PlayerManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // 現在再生中の曲情報を取得
    val currentSongId by playerManager.currentSongId.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    
    // アルバム内の曲を取得
    val songs by musicRepository.getSongsByAlbum(albumName).collectAsState(initial = emptyList())
    
    var selectedBottomTabIndex by remember { mutableStateOf(TabItem.ALBUMS.ordinal) }
    
    // 選択モードの状態
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistOptions by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    
    // 選択中の曲がなくなったら自動で選択モードを解除
    LaunchedEffect(selectedSongs) {
        if (selectedSongs.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }
    
    // ボトムシートの状態
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    
    // システムのバックボタンの処理
    androidx.activity.compose.BackHandler(enabled = !isSelectionMode) {
        onBack()
    }
    
    // 選択モード時は選択モードを解除
    androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongs = emptySet()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("選択中")
                    } else {
                        Text(
                            text = albumName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "キャンセル")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                        }
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
                                    if (index != TabItem.ALBUMS.ordinal) {
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
                                        contentDescription = tabItem.getTitle(LocalContext.current),
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
                EmptyState(message = "このアルバムに曲がありません")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(songs.sortedBy { it.trackNumber ?: Int.MAX_VALUE }, key = { it.id }) { song ->
                        val isSelected = selectedSongs.contains(song.id)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedSongs = if (isSelected) {
                                                selectedSongs - song.id
                                            } else {
                                                selectedSongs + song.id
                                            }
                                        } else {
                                            onSongSelected(song.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedSongs = setOf(song.id)
                                        }
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected && isSelectionMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // アルバムアートまたはデフォルトアイコン
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .padding(end = 12.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                ) {
                                    if (song.albumArtPath != null && song.albumArtPath.isNotBlank()) {
                                        // タイムスタンプパラメータを削除して元のパスを取得（表示用）
                                        val imagePathForDisplay = song.albumArtPath.substringBefore("?t=").substringBefore("&t=")
                                        // ファイルパスからFileオブジェクトを作成して使用
                                        val imageFile = java.io.File(imagePathForDisplay)
                                        val imagePainter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(imageFile)
                                                .crossfade(true)
                                                .build()
                                        )
                                        
                                        when (imagePainter.state) {
                                            is AsyncImagePainter.State.Loading,
                                            is AsyncImagePainter.State.Error -> {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = song.title,
                                                        modifier = Modifier.size(32.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            else -> {
                                                AsyncImage(
                                                    model = imagePainter.request,
                                                    contentDescription = song.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = song.title,
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                // ケバブメニュー
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "メニュー"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("詳細") },
                                            onClick = {
                                                showMenu = false
                                                onSongDetail(song.id)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Info, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 選択モード時のオプションボタン
                if (isSelectionMode && selectedSongs.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showPlaylistOptions = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "オプション"
                        )
                    }
                }
            }
        }
    } // Scaffoldのcontentラムダ終了
    
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
                },
                onEditLyrics = {
                    onEditLyrics(currentSongId!!)
                }
            )
        }
    }

    // プレイリストオプションメニュー
    if (showPlaylistOptions) {
        PlaylistOptionsMenu(
            selectedSongs = selectedSongs,
            onDismiss = { showPlaylistOptions = false },
            onCreatePlaylist = {
                showPlaylistOptions = false
                showCreatePlaylistDialog = true
            },
            onAddToPlaylist = {
                showPlaylistOptions = false
                showAddToPlaylistDialog = true
            }
        )
    }

    // プレイリスト作成ダイアログ
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { playlistName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val playlistId = playlistRepository.createPlaylist(playlistName)
                        selectedSongs.forEachIndexed { index, songId ->
                            playlistRepository.addSongToPlaylist(playlistId, songId, index)
                        }
                    }
                    showCreatePlaylistDialog = false
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }
            }
        )
    }

    // プレイリスト選択ダイアログ
    if (showAddToPlaylistDialog) {
        SelectPlaylistDialog(
            selectedSongs = selectedSongs,
            onDismiss = { showAddToPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val currentSongs = playlistRepository.getSongsInPlaylist(playlistId).first()
                        selectedSongs.forEachIndexed { index, songId ->
                            playlistRepository.addSongToPlaylist(playlistId, songId, currentSongs.size + index)
                        }
                    }
                    showAddToPlaylistDialog = false
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }
            }
        )
    }
}
