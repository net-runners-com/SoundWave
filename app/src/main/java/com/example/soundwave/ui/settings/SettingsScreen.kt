package com.example.soundwave.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundwave.ui.home.HomeViewModel
import com.example.soundwave.ui.home.HomeViewModelFactory
import com.example.soundwave.ui.theme.AppTheme
import com.example.soundwave.ui.theme.ThemeManager
import com.example.soundwave.ui.settings.WidgetSettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit = {},
    onWidgetSettingsClick: () -> Unit = {},
    onVersionHistoryClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = packageInfo.longVersionCode
    
    val isScanning by viewModel.isScanning.collectAsState()
    val themeManager = remember { ThemeManager(context) }
    var selectedTheme by remember { mutableStateOf(themeManager.getSelectedTheme()) }
    
    var backgroundPlaybackEnabled by remember { 
        mutableStateOf(AppSettingsManager.isBackgroundPlaybackEnabled(context)) 
    }
    var stopOnOtherAppEnabled by remember { 
        mutableStateOf(AppSettingsManager.isStopOnOtherAppEnabled(context)) 
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // テーマ設定
            item {
                Text(
                    text = "外観",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "テーマカラー",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AppTheme.values().forEach { theme ->
                                ThemeColorOption(
                                    theme = theme,
                                    isSelected = selectedTheme == theme,
                                    onClick = {
                                        selectedTheme = theme
                                        themeManager.setSelectedTheme(theme)
                                        onThemeChanged(theme)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 音楽スキャン設定
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "音楽スキャン",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "音楽ファイルを再スキャン",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "デバイス内の音楽ファイルを再検索します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.scanMusicFiles() },
                                enabled = !isScanning
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("スキャン中...")
                                } else {
                                    Text("スキャン")
                                }
                            }
                        }
                    }
                }
            }
            
            // 詳細設定
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "詳細",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "バックグラウンド再生を許可",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Switch(
                                checked = backgroundPlaybackEnabled,
                                onCheckedChange = {
                                    backgroundPlaybackEnabled = it
                                    AppSettingsManager.setBackgroundPlaybackEnabled(context, it)
                                }
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "他アプリでオーディオ再生",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "他アプリで再生した場合、SoundWaveの音楽を停止",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = stopOnOtherAppEnabled,
                                onCheckedChange = {
                                    stopOnOtherAppEnabled = it
                                    AppSettingsManager.setStopOnOtherAppEnabled(context, it)
                                }
                            )
                        }
                    }
                }
            }
            
            // アプリ情報
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "アプリ情報",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "バージョン",
                            subtitle = "$versionName ($versionCode)",
                            onClick = onVersionHistoryClick
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        SettingsItem(
                            icon = Icons.Default.Apps,
                            title = "アプリ名",
                            subtitle = "SoundWave"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ThemeColorOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColor = when (theme) {
        AppTheme.PURPLE -> Color(0xFF6650a4)
        AppTheme.BLUE -> Color(0xFF1976D2)
        AppTheme.GREEN -> Color(0xFF388E3C)
        AppTheme.ORANGE -> Color(0xFFF57C00)
        AppTheme.RED -> Color(0xFFC62828)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawCircle(color = themeColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

