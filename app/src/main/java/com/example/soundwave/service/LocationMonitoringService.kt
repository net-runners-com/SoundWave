package com.example.soundwave.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.soundwave.MainActivity
import com.example.soundwave.R
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.database.LocationCircleEntity
import com.example.soundwave.player.PlayerManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlin.math.*

class LocationMonitoringService : Service() {
    
    companion object {
        private const val TAG = "LocationMonitoring"
        private const val CHANNEL_ID = "location_monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5秒
        private const val LOCATION_FASTEST_INTERVAL = 3000L // 3秒
        
        const val ACTION_START_MONITORING = "com.example.soundwave.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.soundwave.STOP_MONITORING"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val locationCircleRepository by lazy {
        AppDatabaseModule.getLocationCircleRepository(applicationContext)
    }
    private val playerManager by lazy {
        PlayerManager.getInstance(applicationContext)
    }
    private val musicRepository by lazy {
        AppDatabaseModule.getMusicRepository(applicationContext)
    }
    
    private var wasInCircle: Boolean = false
    private var currentCircleId: Long? = null
    private var currentLocation: Location? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    serviceScope.launch(Dispatchers.IO) {
                        checkLocationInCircles(location)
                    }
                }
            }
        }
        
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                Log.d(TAG, "Starting location monitoring...")
                startLocationUpdates()
                Log.d(TAG, "Location monitoring started")
            }
            ACTION_STOP_MONITORING -> {
                Log.d(TAG, "Stopping location monitoring...")
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Location monitoring stopped")
            }
            null -> {
                // サービスが再起動された場合も監視を開始
                Log.d(TAG, "Service restarted, starting location monitoring...")
                startLocationUpdates()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "位置情報監視",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "円内判定のために位置情報を監視しています"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates called")
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "Foreground service started with notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } else {
                @Suppress("DEPRECATION")
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
            Log.d(TAG, "Location updates requested successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped")
    }
    
    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("位置情報監視中")
            .setContentText("円内判定のために位置情報を監視しています")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private suspend fun checkLocationInCircles(location: Location) {
        try {
            val circles = locationCircleRepository.getAllCirclesSync()
            Log.d(TAG, "Checking location ${location.latitude}, ${location.longitude} against ${circles.size} circles")
            if (circles.isEmpty()) {
                Log.d(TAG, "No circles to check")
                return
            }
            
            var isInCircle = false
            var matchedCircle: LocationCircleEntity? = null
            
            // すべての円をチェックして、現在位置が含まれるか確認
            for (circle in circles) {
                val distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    circle.latitude,
                    circle.longitude
                )
                
                if (distance <= circle.radius) {
                    isInCircle = true
                    matchedCircle = circle
                    Log.d(TAG, "Location is inside circle: ${circle.name}, distance: $distance m, radius: ${circle.radius} m")
                    break
                }
            }
            
            // 状態が変わったときの処理
            if (isInCircle && !wasInCircle) {
                // 円内に入った
                matchedCircle?.let { circle ->
                    currentCircleId = circle.id
                    withContext(Dispatchers.Main) {
                        if (circle.playlistId != null && circle.playlistId != 0L) {
                            playerManager.playPlaylist(circle.playlistId)
                            Log.d(TAG, "Playing playlist: ${circle.playlistId}")
                        } else {
                            playLatestSong()
                            Log.d(TAG, "Playing latest song")
                        }
                    }
                }
            } else if (!isInCircle && wasInCircle) {
                // 円から出た
                currentCircleId = null
                withContext(Dispatchers.Main) {
                    playerManager.pause()
                    playerManager.clearPlaylistMode()
                    Log.d(TAG, "Stopped music - exited circle")
                }
            }
            
            wasInCircle = isInCircle
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location in circles", e)
        }
    }
    
    private suspend fun playLatestSong() {
        try {
            val allSongs = musicRepository.getAllSongsSync()
            val latestSong = allSongs.maxByOrNull { it.dateAdded }
            
            if (latestSong != null) {
                withContext(Dispatchers.Main) {
                    playerManager.playSong(latestSong.filePath, latestSong.id)
                }
                Log.d(TAG, "Playing latest song: ${latestSong.title}")
            } else {
                Log.w(TAG, "No songs found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing latest song", e)
        }
    }
    
    // Haversine formulaを使用して2点間の距離を計算（メートル単位）
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // 地球の半径（メートル）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}

