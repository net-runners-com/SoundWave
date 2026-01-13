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
import android.app.Activity
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
    onWidgetSettingsClick: () -> Unit = {}, // Reserved for future use
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
                title = { Text(context.getString(com.example.soundwave.R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = context.getString(com.example.soundwave.R.string.back)
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
                    text = context.getString(com.example.soundwave.R.string.settings_appearance),
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
                            text = context.getString(com.example.soundwave.R.string.settings_theme_color),
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
                                    text = context.getString(com.example.soundwave.R.string.settings_rescan_music),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = context.getString(com.example.soundwave.R.string.settings_rescan_complete),
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
                                    Text(context.getString(com.example.soundwave.R.string.settings_rescanning))
                                } else {
                                    Text(context.getString(com.example.soundwave.R.string.search))
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
                    text = context.getString(com.example.soundwave.R.string.settings_details),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // 言語設定
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        var selectedLanguage by remember { 
                            mutableStateOf(LanguageManager.getSelectedLanguage(context))
                        }
                        var showLanguageDialog by remember { mutableStateOf(false) }
                        
                        SettingsItem(
                            icon = Icons.Default.Language,
                            title = context.getString(com.example.soundwave.R.string.settings_language),
                            subtitle = LanguageManager.getSupportedLanguages()
                                .find { it.code == selectedLanguage }?.nativeName
                                ?: context.getString(com.example.soundwave.R.string.language_japanese),
                            onClick = { showLanguageDialog = true }
                        )
                        
                        if (showLanguageDialog) {
                            AlertDialog(
                                onDismissRequest = { showLanguageDialog = false },
                                title = { 
                                    Text(context.getString(com.example.soundwave.R.string.settings_language))
                                },
                                text = {
                                    Column {
                                        LanguageManager.getSupportedLanguages().forEach { language ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedLanguage = language.code
                                                        LanguageManager.setLanguage(context, language.code)
                                                        showLanguageDialog = false
                                                        // Activityを再起動して言語変更を反映
                                                        (context as? Activity)?.recreate()
                                                    }
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selectedLanguage == language.code,
                                                    onClick = {
                                                        selectedLanguage = language.code
                                                        LanguageManager.setLanguage(context, language.code)
                                                        showLanguageDialog = false
                                                        // Activityを再起動して言語変更を反映
                                                        (context as? Activity)?.recreate()
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = language.nativeName,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = " (${language.englishName})",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showLanguageDialog = false }) {
                                        Text(context.getString(com.example.soundwave.R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // 詳細設定（続き）
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
                                    text = context.getString(com.example.soundwave.R.string.settings_allow_background_playback),
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
                                    text = context.getString(com.example.soundwave.R.string.settings_stop_on_other_app_audio),
                                    style = MaterialTheme.typography.bodyLarge
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
                            title = context.getString(com.example.soundwave.R.string.settings_version),
                            subtitle = "$versionName ($versionCode)",
                            onClick = onVersionHistoryClick
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        SettingsItem(
                            icon = Icons.Default.Apps,
                            title = context.getString(com.example.soundwave.R.string.app_name),
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

