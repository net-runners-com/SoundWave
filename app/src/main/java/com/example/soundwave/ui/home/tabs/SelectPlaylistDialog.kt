@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.soundwave.ui.home.tabs

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.ui.components.EmptyState
import com.example.soundwave.ui.components.ListItemCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun SelectPlaylistDialog(
    selectedSongs: Set<Long>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val playlistRepository = remember { AppDatabaseModule.getPlaylistRepository(context) }
    val scope = rememberCoroutineScope()
    
    var playlists by remember { mutableStateOf<List<com.example.soundwave.data.database.PlaylistEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            playlists = playlistRepository.getAllPlaylists().first()
            isLoading = false
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        }
    ) {
        val configuration = LocalConfiguration.current
        val screenHeight = with(LocalDensity.current) {
            configuration.screenHeightDp.dp.toPx()
        }
        val maxHeight = with(LocalDensity.current) {
            (screenHeight / 2).toDp()
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "プレイリストを選択",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (playlists.isEmpty()) {
                EmptyState(message = "プレイリストがありません")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItemCard(
                            title = playlist.name,
                            onClick = {
                                onPlaylistSelected(playlist.id)
                                scope.launch {
                                    bottomSheetState.hide()
                                }.invokeOnCompletion {
                                    if (!bottomSheetState.isVisible) {
                                        onDismiss()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

