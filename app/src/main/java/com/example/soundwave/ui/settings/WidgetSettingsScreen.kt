package com.example.soundwave.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.soundwave.widget.WidgetSettings
import com.example.soundwave.widget.WidgetSettingsManager
import com.example.soundwave.widget.WidgetTheme
import com.example.soundwave.widget.WidgetContentType
import com.example.soundwave.widget.MusicPlayerWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember {
        mutableStateOf(WidgetSettingsManager.loadSettings(context))
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ウィジェット設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    WidgetSettingsManager.saveSettings(context, settings)
                    // ウィジェットを更新（設定保存時は曲情報なしで更新）
                    MusicPlayerWidget.updateAllWidgets(context, null, null, null, false)
                    onBack()
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ウィジェットプレビュー
            item {
                WidgetPreview(settings = settings)
            }
            
            // ウィジェットテーマ設定
            item {
                Text(
                    text = "ウィジェットテーマ:",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetTheme.values().forEach { theme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        settings = settings.copy(theme = theme)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.theme == theme,
                                    onClick = {
                                        settings = settings.copy(theme = theme)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (theme) {
                                        WidgetTheme.LIGHT -> "ライトテーマ"
                                        WidgetTheme.DARK -> "ダークテーマ"
                                        WidgetTheme.SYSTEM -> "システムに合わせる"
                                        WidgetTheme.ALBUM_ART -> "アルバムアートの色に合わせる"
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
            
            // 透明度設定
            item {
                Text(
                    text = "透明度",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${settings.transparency}%",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Slider(
                            value = settings.transparency.toFloat(),
                            onValueChange = { value ->
                                settings = settings.copy(transparency = value.toInt())
                            },
                            valueRange = 0f..100f,
                            steps = 9
                        )
                    }
                }
            }
            
            // ウィジェットコンテンツ設定
            item {
                Text(
                    text = "ウィジェットコンテンツ",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // タブ
                        var selectedTab by remember { mutableStateOf(settings.contentType) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WidgetContentType.values().forEach { contentType ->
                                FilterChip(
                                    selected = selectedTab == contentType,
                                    onClick = {
                                        selectedTab = contentType
                                        settings = settings.copy(contentType = contentType)
                                    },
                                    label = {
                                        Text(
                                            when (contentType) {
                                                WidgetContentType.SIMPLE -> "シンプル"
                                                WidgetContentType.ADVANCED -> "高度"
                                                WidgetContentType.CURRENT_QUEUE -> "現在のキュー"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // オプション
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = settings.showAlbumArt,
                                onCheckedChange = { checked ->
                                    settings = settings.copy(showAlbumArt = checked)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "アルバムアート",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = settings.showAddToPlaylist,
                                onCheckedChange = { checked ->
                                    settings = settings.copy(showAddToPlaylist = checked)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "プレイリストに追加する",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(settings: WidgetSettings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when (settings.theme) {
                        WidgetTheme.LIGHT -> Color.White
                        WidgetTheme.DARK -> Color.Black
                        WidgetTheme.SYSTEM -> MaterialTheme.colorScheme.surface
                        WidgetTheme.ALBUM_ART -> Color(0xFF8B4513) // アルバムアートの色の例
                    }
                )
                .then(
                    if (settings.transparency > 0) {
                        Modifier.background(
                            Color.White.copy(alpha = (100 - settings.transparency) / 100f)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 前の曲ボタン
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (settings.theme == WidgetTheme.LIGHT) Color.Black else Color.White
                )
                
                // 再生ボタン
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (settings.theme == WidgetTheme.LIGHT) Color.Black else Color.White
                )
                
                // 曲情報
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "タイトル",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (settings.theme == WidgetTheme.LIGHT) Color.Black else Color.White
                    )
                    Text(
                        text = "アーティスト",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.theme == WidgetTheme.LIGHT) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
                    )
                }
                
                // 次の曲ボタン
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (settings.theme == WidgetTheme.LIGHT) Color.Black else Color.White
                )
            }
        }
    }
}

