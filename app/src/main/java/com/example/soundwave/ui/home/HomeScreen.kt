@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.example.soundwave.ui.components.TabItem
import com.example.soundwave.ui.components.NowPlayingFAB
import com.example.soundwave.ui.components.CreatePlaylistDialog
import com.example.soundwave.ui.home.tabs.*
import com.example.soundwave.ui.player.PlayerScreen
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.data.AppDatabaseModule

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    ),
    onSongSelected: (Long) -> Unit = {},
    onAlbumSelected: (String) -> Unit = {},
    onArtistSelected: (String) -> Unit = {},
    onFolderSelected: (String) -> Unit = {},
    onPlaylistSelected: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // 現在再生中の曲情報を取得
    val playerManager = remember { PlayerManager.getInstance(context) }
    val currentSongId by playerManager.currentSongId.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    
    // ボトムシートの状態
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    
    // 選択モードの状態
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistOptionsFromHeader by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var clearSelectionTrigger by remember { mutableStateOf(false) }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(240.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "SoundWave",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Divider()
                
                // タブのアイテム
                TabItem.values().forEachIndexed { index, tabItem ->
                    NavigationDrawerItem(
                        icon = { Icon(tabItem.icon, contentDescription = null) },
                        label = { Text(tabItem.title) },
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                
                Divider()
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("設定") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                        onSettingsClick()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        if (isSelectionMode) {
                            Text("選択中")
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SoundWave",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedSongs = emptySet()
                                clearSelectionTrigger = !clearSelectionTrigger
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "キャンセル"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "メニュー"
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            var showMenu by remember { mutableStateOf(false) }
                            
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "メニュー"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("新しいプレイリストを作成") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showCreatePlaylistDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("既存のプレイリストに追加") },
                                        leadingIcon = {
                                            Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showAddToPlaylistDialog = true
                                        }
                                    )
                                }
                            }
                        } else {
                            val isScanning by viewModel.isScanning.collectAsState()
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(16.dp)
                                )
                            }
                            IconButton(onClick = { onSettingsClick() }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "設定"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    // 現在再生中の曲を表示（再生中の場合のみ）
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
                    
                    // タブバー
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TabItem.values().forEachIndexed { index, tabItem ->
                                val isSelected = selectedTabIndex == index
                                IconButton(
                                    onClick = { selectedTabIndex = index },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // スムーズなタブ切り替え（Crossfadeで軽量）
            Crossfade(
                targetState = selectedTabIndex,
                animationSpec = tween(200),
                label = "tab_transition"
            ) { tabIndex ->
                // タブコンテンツを表示（データは既にキャッシュされているので再読み込みなし）
                when (TabItem.values().getOrNull(tabIndex)) {
                    TabItem.SONGS -> SongsTab(
                        viewModel = viewModel,
                        onSongSelected = onSongSelected,
                        onSelectionModeChanged = { isSelectionMode = it },
                        onSelectedSongsChanged = { selectedSongs = it },
                        onShowPlaylistOptions = { showPlaylistOptionsFromHeader = true },
                        externalClearSelection = clearSelectionTrigger
                    )
                    TabItem.ALBUMS -> AlbumsTab(
                        viewModel = viewModel,
                        onAlbumSelected = onAlbumSelected
                    )
                    TabItem.ARTISTS -> ArtistsTab(
                        viewModel = viewModel,
                        onArtistSelected = onArtistSelected
                    )
                    TabItem.FOLDERS -> FoldersTab(
                        viewModel = viewModel,
                        onFolderSelected = onFolderSelected
                    )
                    TabItem.PLAYLISTS -> PlaylistsTab(
                        viewModel = viewModel,
                        onPlaylistSelected = onPlaylistSelected
                    )
                    TabItem.YOUTUBE -> YouTubeTab()
                    null -> {}
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
    
    // ヘッダーから開いたプレイリストオプションメニュー
    if (showPlaylistOptionsFromHeader && isSelectionMode && selectedSongs.isNotEmpty()) {
        PlaylistOptionsMenu(
            selectedSongs = selectedSongs,
            onDismiss = { showPlaylistOptionsFromHeader = false },
            onCreatePlaylist = {
                showPlaylistOptionsFromHeader = false
                showCreatePlaylistDialog = true
            },
            onAddToPlaylist = {
                showPlaylistOptionsFromHeader = false
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
