package com.example.soundwave.ui.home.tabs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.GeolocationPermissions
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


private val leafletHtml = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />

  <link
    rel="stylesheet"
    href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    crossorigin=""
  />
  <link
    rel="stylesheet"
    href="https://unpkg.com/leaflet-draw@1.0.4/dist/leaflet.draw.css"
    crossorigin=""
  />

  <script
    src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
    crossorigin=""
  ></script>
  <script
    src="https://unpkg.com/leaflet-draw@1.0.4/dist/leaflet.draw.js"
    crossorigin=""
  ></script>

   <style>
     html, body {
       width: 100%;
       height: 100%;
       margin: 0;
     }
     #map {
       position: fixed;
       inset: 0;
       width: 100%;
       height: 100%;
       background: #ddd;
     }
     .leaflet-touch .leaflet-control-zoom-display {
       width: 48px;
       height: 48px;
       font-size: 18px;
       line-height: 30px;
    }
    .leaflet-touch .leaflet-bar a, .leaflet-touch .leaflet-toolbar-0 > li > a {
       width: 44px;
       height: 44px;
       font-size: 20px;
       line-height: 45px;
       background-size: 314px 30px;
    }
    .leaflet-touch .leaflet-draw-toolbar.leaflet-bar a {
       background-position-y: 6px;
    }
    .leaflet-touch .leaflet-draw-actions a, .leaflet-touch .leaflet-control-toolbar .leaflet-toolbar-1 > li > .leaflet-toolbar-icon {
       font-size: 20px;
       line-height: 44px;
       height: 44px;
    }
     .leaflet-touch .leaflet-draw-actions, .leaflet-touch .leaflet-toolbar-1 {
      left: 45px;
     }
     .current-location-marker {
       background: transparent !important;
       border: none !important;
     }
     .current-location-marker-highlight {
       background: transparent !important;
       border: none !important;
     }
   </style>
</head>
<body>

<div id="map"></div>

<script>
  console.log("Leaflet loaded:", window.L);

  // SharedPreferencesから保存された位置情報を取得
  let savedLat = 0;
  let savedLng = 0;
  
  if (typeof AndroidLocation !== 'undefined') {
    savedLat = AndroidLocation.getLastLat();
    savedLng = AndroidLocation.getLastLng();
    console.log("Saved location from SharedPreferences:", savedLat, savedLng);
  }
  
  // 保存された位置情報があればそれを使用、なければデフォルト位置（六本木）
  const defaultLat = (savedLat !== 0) ? savedLat : 35.66572;
  const defaultLng = (savedLng !== 0) ? savedLng : 139.73100;
  
  console.log("Initial map center:", defaultLat, defaultLng);
  
  // 地図を初期化
  const map = L.map("map").setView([defaultLat, defaultLng], 17);
  window.map = map;

  L.tileLayer(
    "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    { attribution: "© OpenStreetMap contributors" }
  ).addTo(map);

  // 現在地マーカーをカスタムアイコンで作成
  const currentLocationIcon = L.divIcon({
    className: 'current-location-marker',
    html: '<div style="background-color: #2196F3; width: 20px; height: 20px; border-radius: 50%; border: 3px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>',
    iconSize: [20, 20],
    iconAnchor: [10, 10]
  });
  
  // 強調表示用のマーカーアイコン（円の中にいる場合）
  const currentLocationHighlightIcon = L.divIcon({
    className: 'current-location-marker-highlight',
    html: '<div style="background-color: #FF5722; width: 28px; height: 28px; border-radius: 50%; border: 4px solid white; box-shadow: 0 4px 8px rgba(255,87,34,0.6); animation: pulse 1.5s infinite;"></div>',
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  });
  
  // パルスアニメーション用のCSS
  const style = document.createElement('style');
  style.textContent = `
    @keyframes pulse {
      0% { transform: scale(1); opacity: 1; }
      50% { transform: scale(1.1); opacity: 0.8; }
      100% { transform: scale(1); opacity: 1; }
    }
  `;
  document.head.appendChild(style);
  
  let marker = L.marker([defaultLat, defaultLng], { icon: currentLocationIcon }).addTo(map);
  window.marker = marker;

  // 現在地が円の中にあるかどうかを判定する関数
  function isLocationInCircle(location, circle) {
    const circleCenter = circle.getLatLng();
    const circleRadius = circle.getRadius(); // メートル単位
    const distance = location.distanceTo(circleCenter); // メートル単位
    
    return distance <= circleRadius;
  }
  
  // 位置情報が取得されたときの処理
  map.on('locationfound', function(e) {
    const lat = e.latlng.lat;
    const lng = e.latlng.lng;
    const currentLocation = L.latLng(lat, lng);
    console.log("Location found:", lat, lng);
    
    // 描画された円をチェックして、現在地が円の中にあるかどうかを判定
    let isInCircle = false;
    let circleName = null;
    let wasInCircle = window.wasInCircle || false; // 前回の状態を保持
    
    drawnItems.eachLayer(function(layer) {
      if (layer instanceof L.Circle) {
        if (isLocationInCircle(currentLocation, layer)) {
          isInCircle = true;
          // 円の名前を取得
          if (layer.feature && layer.feature.properties && layer.feature.properties.title) {
            circleName = layer.feature.properties.title;
            console.log("Current location is inside a circle! Circle name: " + circleName);
          } else {
            console.log("Current location is inside a circle! (no name)");
          }
        }
      }
    });
    
    // 円の中に入った瞬間（前回は外にいて、今回は中にいる）に音楽を再生
    if (isInCircle && !wasInCircle) {
      console.log("Entered circle! Playing latest song.");
      if (typeof AndroidLocation !== 'undefined') {
        AndroidLocation.playLatestSong();
      }
    }
    
    // 円から外れた瞬間（前回は中にいて、今回は外にいる）に音楽を停止
    if (!isInCircle && wasInCircle) {
      console.log("Exited circle! Stopping music.");
      if (typeof AndroidLocation !== 'undefined') {
        AndroidLocation.stopMusic();
      }
    }
    
    // 状態を更新
    window.wasInCircle = isInCircle;
    
    // マーカーの位置を更新
    // アイコンを変更するために、マーカーを削除して再作成
    const newIcon = isInCircle ? currentLocationHighlightIcon : currentLocationIcon;
    
    if (marker) {
      // 既存のマーカーを削除
      map.removeLayer(marker);
    }
    
    // 新しいアイコンでマーカーを再作成
    marker = L.marker([lat, lng], { icon: newIcon }).addTo(map);
    window.marker = marker;
    
    if (isInCircle) {
      console.log("Marker recreated with highlight icon" + (circleName ? " - Inside circle: " + circleName : ""));
    } else {
      console.log("Marker recreated with normal icon");
    }
    
    // 初回のみ地図の中心を移動
    if (!window.initialLocationSet) {
      map.setView([lat, lng], 17);
      window.initialLocationSet = true;
      console.log("Initial view set to current location");
    }
    
    // 位置情報をSharedPreferencesに保存
    if (typeof AndroidLocation !== 'undefined') {
      AndroidLocation.saveLocation(lat, lng);
    }
  });

  // 位置情報の取得に失敗したときの処理
  map.on('locationerror', function(e) {
    console.error("Location error:", e.message);
  });

  // リアルタイム位置追跡を開始
  map.locate({
    watch: true,
    setView: false,
    maxZoom: 17,
    enableHighAccuracy: true,
    timeout: 10000,
    maximumAge: 0
  });

  // 描画した円を保持
  let currentCircle = null;
  let drawnItems = new L.FeatureGroup();
  map.addLayer(drawnItems);

  // Leaflet Drawの設定
  const drawControl = new L.Control.Draw({
    draw: {
      polygon: false,
      polyline: false,
      rectangle: false,
      marker: false,
      circle: true,
      circlemarker: false
    },
    edit: {
      featureGroup: drawnItems,
      remove: true
    }
  });
  map.addControl(drawControl);

  // 円の描画が開始されたときの処理
  map.on(L.Draw.Event.DRAWSTART, function (e) {
    const type = e.layerType;
    if (type === 'circle') {
      map.dragging.disable();
      map.touchZoom.disable();
      map.doubleClickZoom.disable();
      map.scrollWheelZoom.disable();
      map.boxZoom.disable();
      map.keyboard.disable();
      if (map.tap) map.tap.disable();
      console.log("Map interaction disabled for circle drawing");
    }
  });

  // 円の描画が停止されたときの処理
  map.on(L.Draw.Event.DRAWSTOP, function (e) {
    map.dragging.enable();
    map.touchZoom.enable();
    map.doubleClickZoom.enable();
    map.scrollWheelZoom.enable();
    map.boxZoom.enable();
    map.keyboard.enable();
    if (map.tap) map.tap.enable();
    console.log("Map interaction enabled");
  });

  // 円が描画されたときの処理
  map.on(L.Draw.Event.CREATED, function (e) {
    const layer = e.layer;
    const type = e.layerType;

    if (type === 'circle') {
      currentCircle = layer;
      
      const title = prompt("名前を入力してください", "デフォルト");
      
      if (!layer.feature) {
        layer.feature = {};
      }
      layer.feature.properties = layer.feature.properties || {};
      layer.feature.properties.title = title || "デフォルト";
      
      const popupContent = '<b>名前: </b>' + (title || "デフォルト");
      layer.bindPopup(popupContent, {
        'maxWidth': '400',
        'className': 'PopupDrawCircle'
      });
      
      layer.bindTooltip(title || "デフォルト", {
        permanent: true,
        direction: 'center',
        offset: [0, 0],
        className: 'draw-label',
        noWrap: true
      });
      
      drawnItems.addLayer(layer);
    }
  });

  // 円が削除されたときの処理
  map.on(L.Draw.Event.DELETED, function (e) {
    const layers = e.layers;
    layers.eachLayer(function (layer) {
      if (layer === currentCircle) {
        currentCircle = null;
      }
    });
  });

  console.log("map initialized");
</script>

</body>
</html>
""".trimIndent()


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapTab() {
    val context = LocalContext.current
    
    // 位置情報の権限をリクエスト
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    // 権限が許可されていない場合のUI
    if (!locationPermissionsState.allPermissionsGranted) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "位置情報の権限が必要です",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "地図を表示するには、位置情報へのアクセス権限が必要です。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { locationPermissionsState.launchMultiplePermissionRequest() }
                ) {
                    Text("権限を許可")
                }
            }
        }
        return
    }
    
    WebView.setWebContentsDebuggingEnabled(true)
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setGeolocationEnabled(true)
                }
    
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "WebView",
                            "[${msg.lineNumber()}] ${msg.message()}"
                        )
                        return true
                    }
                    
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }
                }
    
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
  
                        view.evaluateJavascript(
                            """
                            if (window.map) {
                              window.map.invalidateSize();
                              console.log("invalidateSize called");
                            }
                            """.trimIndent(),
                            null
                        )
                    }
                }
  
                addJavascriptInterface(LocationSaveInterface(ctx), "AndroidLocation")
  
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    leafletHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
                
                post {
                    evaluateJavascript(
                        """
                        if (window.map) {
                          map.invalidateSize();
                          console.log("forced invalidate after layout");
                        }
                        """.trimIndent(),
                        null
                    )
                }
            }
        },
        update = { }
    )
}

class LocationSaveInterface(private val context: Context) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicRepository = AppDatabaseModule.getMusicRepository(context)
    private val playerManager = PlayerManager.getInstance(context)
    
    @JavascriptInterface
    fun saveLocation(lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences("map_location_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("last_lat", lat.toFloat())
            putFloat("last_lng", lng.toFloat())
            apply()
        }
        android.util.Log.d("LocationSave", "Saved location: $lat, $lng")
    }
    
    @JavascriptInterface
    fun getLastLat(): Double {
        val prefs = context.getSharedPreferences("map_location_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 0f)
        return if (lat != 0f) lat.toDouble() else 0.0
    }
    
    @JavascriptInterface
    fun getLastLng(): Double {
        val prefs = context.getSharedPreferences("map_location_prefs", Context.MODE_PRIVATE)
        val lng = prefs.getFloat("last_lng", 0f)
        return if (lng != 0f) lng.toDouble() else 0.0
    }
    
    @JavascriptInterface
    fun playLatestSong() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 最新の曲を取得（dateAddedでソート、最新の1曲）
                val allSongs = musicRepository.getAllSongsSync()
                val latestSong = allSongs.maxByOrNull { it.dateAdded }
                
                if (latestSong != null) {
                    android.util.Log.d("LocationSave", "Playing latest song: ${latestSong.title}")
                    playerManager.playSong(latestSong.filePath, latestSong.id)
                } else {
                    android.util.Log.w("LocationSave", "No songs found")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error playing latest song", e)
            }
        }
    }
    
    @JavascriptInterface
    fun stopMusic() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                android.util.Log.d("LocationSave", "Stopping music")
                playerManager.pause()
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error stopping music", e)
            }
        }
    }
}