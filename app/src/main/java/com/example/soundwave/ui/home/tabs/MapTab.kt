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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.*


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

  <script
    src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
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
       background: #ddd; /* デバッグ用 */
     }
   </style>
</head>
<body>

<div id="map"></div>

<script>
  console.log("Leaflet loaded:", window.L);

  // デフォルト位置（六本木）
  let defaultLat = 35.66572;
  let defaultLng = 139.73100;
  
  const map = L.map("map").setView([defaultLat, defaultLng], 17);
  window.map = map;

  L.tileLayer(
    "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    { attribution: "© OpenStreetMap contributors" }
  ).addTo(map);

  let marker = L.marker([defaultLat, defaultLng]).addTo(map);
  marker.bindPopup("現在地").openPopup();
  window.marker = marker;

  console.log("map initialized");
  
  // Androidから位置情報を取得できる場合
  if (typeof AndroidLocation !== 'undefined' && AndroidLocation.hasLocation()) {
    const lat = AndroidLocation.getLatitude();
    const lng = AndroidLocation.getLongitude();
    const newLatLng = L.latLng(lat, lng);
    map.setView(newLatLng, 17);
    marker.setLatLng(newLatLng);
    marker.bindPopup("現在地").openPopup();
    console.log("Location updated from Android:", lat, lng);
  }
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
      
      var currentLocation by remember { mutableStateOf<Location?>(null) }
      
      // 位置情報を取得
      LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
          if (locationPermissionsState.allPermissionsGranted) {
              try {
                  val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                  fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                      if (location != null) {
                          currentLocation = location
                      } else {
                          // 最後の位置情報がない場合は、新しい位置情報をリクエスト
                          val locationRequest = LocationRequest.Builder(
                              Priority.PRIORITY_HIGH_ACCURACY,
                              10000L
                          ).build()
                          
                          val locationCallback = object : LocationCallback() {
                              override fun onLocationResult(locationResult: LocationResult) {
                                  locationResult.lastLocation?.let {
                                      currentLocation = it
                                      fusedLocationClient.removeLocationUpdates(this)
                                  }
                              }
                          }
                          
                          fusedLocationClient.requestLocationUpdates(
                              locationRequest,
                              locationCallback,
                              android.os.Looper.getMainLooper()
                          )
                      }
                  }
              } catch (e: SecurityException) {
                  android.util.Log.e("MapTab", "位置情報の権限がありません", e)
              } catch (e: Exception) {
                  android.util.Log.e("MapTab", "位置情報の取得に失敗", e)
              }
          }
      }
      
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
  
      val webView = remember {
          WebView(context).apply {
              settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  useWideViewPort = true
                  loadWithOverviewMode = true
              }
  
              webChromeClient = object : WebChromeClient() {
                  override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                      android.util.Log.d(
                          "WebView",
                          "[${msg.lineNumber()}] ${msg.message()}"
                      )
                      return true
                  }
              }
  
              webViewClient = object : WebViewClient() {
                  override fun onPageFinished(view: WebView, url: String?) {
                      super.onPageFinished(view, url)

                      // ★ Compose + Leaflet の必須処理
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

              // JavaScriptインターフェースを追加（位置情報を渡すため）
              addJavascriptInterface(LocationInterface(currentLocation), "AndroidLocation")

              loadDataWithBaseURL(
                  null,
                  leafletHtml,
                  "text/html",
                  "UTF-8",
                  null
              )
              
              // ★ レイアウト後に強制的にinvalidateSizeを呼ぶ
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
      }
      
      AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { webView },
          update = { view ->
              // 位置情報が更新されたら、JavaScriptを実行して地図を更新
              currentLocation?.let { loc ->
                  // LocationInterfaceを更新
                  view.addJavascriptInterface(LocationInterface(loc), "AndroidLocation")
                  
                  view.evaluateJavascript(
                      """
                      if (window.map && window.marker) {
                        const ll = L.latLng(${loc.latitude}, ${loc.longitude});
                        window.map.setView(ll, 17);
                        window.marker.setLatLng(ll);
                        window.marker.bindPopup("現在地").openPopup();
                        console.log("Location updated:", ${loc.latitude}, ${loc.longitude});
                      }
                      """.trimIndent(),
                      null
                  )
              }
          }
      )
  }
  
  // JavaScriptから位置情報にアクセスするためのインターフェース
  class LocationInterface(private val location: Location?) {
      @JavascriptInterface
      fun getLatitude(): Double {
          return location?.latitude ?: 35.66572 // デフォルト: 六本木
      }
      
      @JavascriptInterface
      fun getLongitude(): Double {
          return location?.longitude ?: 139.73100 // デフォルト: 六本木
      }
      
      @JavascriptInterface
      fun hasLocation(): Boolean {
          return location != null
      }
  }
  

