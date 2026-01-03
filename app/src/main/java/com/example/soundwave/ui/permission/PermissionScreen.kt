package com.example.soundwave.ui.permission

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            onPermissionsGranted()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SoundWave",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "音楽を楽しむために、以下の権限が必要です：",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionItem(
            title = "ストレージアクセス",
            description = "端末内の音楽ファイルを読み取るために必要です"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionItem(
            title = "通知",
            description = "音楽再生のコントロールを通知に表示するために必要です"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { permissionsState.launchMultiplePermissionRequest() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "権限を許可",
                fontSize = 18.sp
            )
        }
        
        if (!permissionsState.allPermissionsGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "権限が拒否されました。設定から手動で許可してください。",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

