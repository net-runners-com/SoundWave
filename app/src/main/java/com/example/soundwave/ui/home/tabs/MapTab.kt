package com.example.soundwave.ui.home.tabs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.GeolocationPermissions
import com.example.soundwave.data.AppDatabaseModule
import com.example.soundwave.data.repository.MusicRepository
import com.example.soundwave.data.repository.PlaylistRepository
import com.example.soundwave.player.PlayerManager
import com.example.soundwave.service.LocationMonitoringService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
  <!-- Geoman Leaflet -->
  <link
    rel="stylesheet"
    href="https://unpkg.com/@geoman-io/leaflet-geoman-free@latest/dist/leaflet-geoman.css"
  />
  <!-- sidebar-v2 -->
  <link
    rel="stylesheet"
    href="https://unpkg.com/leaflet-sidebar-v2@3.2.4/css/leaflet-sidebar.min.css"
  />

  <script
    src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
    crossorigin=""
  ></script>
  <!-- Geoman Leaflet -->
  <script
    src="https://unpkg.com/@geoman-io/leaflet-geoman-free@latest/dist/leaflet-geoman.min.js"
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
     /* sidebar-v2のスタイル調整 */
     .leaflet-sidebar {
       background-color: #2c2c2c;
       color: white;
     }
     .leaflet-sidebar-tabs > ul > li.active {
       background-color: #6750A4;
       color: white;
     }
     .leaflet-sidebar-tabs > ul > li:hover {
       background-color: #7d6bb3;
     }
     .leaflet-sidebar-content {
       background-color: #2c2c2c;
       color: white;
     }
     .leaflet-sidebar-pane {
       color: white;
     }
     .leaflet-sidebar-tabs > ul {
       background-color: #1a1a1a;
     }
     .leaflet-sidebar-tabs > ul > li > a {
       color: #ccc;
     }
     .leaflet-sidebar-tabs > ul > li.active > a {
       color: white;
     }
     .current-location-marker {
       background: transparent !important;
       border: none !important;
     }
     .current-location-marker-highlight {
       background: transparent !important;
       border: none !important;
     }
     :root {
       --fab-background: #d0bcff;
       --fab-on-background: #381E72;
       --fab-hover: #e9ddff;
     }
     /* Leafletコントロールボタンのサイズを大きく、FABの色を適用 */
     .leaflet-control-zoom a,
     .leaflet-pm-toolbar a {
       width: 48px !important;
       height: 48px !important;
       line-height: 48px !important;
       font-size: 28px !important;
       font-weight: bold !important;
       background-color: var(--fab-background) !important;
       color: var(--fab-on-background) !important;
     }
     .leaflet-control-zoom a:hover,
     .leaflet-pm-toolbar a:hover {
       background-color: var(--fab-hover) !important;
     }
     .leaflet-control-zoom-in,
     .leaflet-control-zoom-out {
       width: 48px !important;
       height: 48px !important;
       line-height: 48px !important;
       font-size: 28px !important;
       font-weight: bold !important;
       background-color: var(--fab-background) !important;
       color: var(--fab-on-background) !important;
       border: none !important;
     }
     .leaflet-control-zoom-in:hover,
     .leaflet-control-zoom-out:hover {
       background-color: var(--fab-hover) !important;
     }
     .leaflet-control-zoom-in span,
     .leaflet-control-zoom-out span {
       font-size: 28px !important;
       line-height: 48px !important;
       color: var(--fab-on-background) !important;
     }
     /* Geoman Leafletツールバーのボタンサイズを大きく、FABの色を適用 */
     .leaflet-pm-toolbar {
       font-size: 20px !important;
     }
     .leaflet-pm-toolbar .button-container {
       width: 48px !important;
       height: 48px !important;
     }
     .leaflet-pm-toolbar .leaflet-buttons-control-button {
       width: 48px !important;
       height: 48px !important;
       display: flex !important;
       align-items: center !important;
       justify-content: center !important;
       background-color: var(--fab-background) !important;
       color: var(--fab-on-background) !important;
       border: none !important;
     }
     .leaflet-pm-toolbar .leaflet-buttons-control-button:hover {
       background-color: var(--fab-hover) !important;
     }
     .leaflet-pm-toolbar .control-icon {
       width: 32px !important;
       height: 32px !important;
       font-size: 32px !important;
       line-height: 32px !important;
       color: var(--fab-on-background) !important;
     }
     .leaflet-pm-icon-circle,
     .leaflet-pm-icon-text,
     .leaflet-pm-icon-edit,
     .leaflet-pm-icon-drag,
     .leaflet-pm-icon-delete {
       font-size: 32px !important;
       width: 32px !important;
       height: 32px !important;
       color: var(--fab-on-background) !important;
     }
     /* 円のtooltip（名前表示）のスタイル */
     .draw-label {
       background: rgba(0, 0, 0, 0.8) !important;
       border: 2px solid #4CAF50 !important;
       border-radius: 8px !important;
       color: white !important;
       font-size: 14px !important;
       font-weight: bold !important;
       padding: 6px 12px !important;
       box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3) !important;
       white-space: nowrap !important;
     }
     .draw-label:before {
       border-top-color: #4CAF50 !important;
     }
     /* 円の名前とプレイリスト選択モーダルのスタイル */
     #circleNamePlaylistModal {
       position: fixed;
       top: 0;
       left: 0;
       width: 100%;
       height: 100%;
       background-color: rgba(0, 0, 0, 0.7);
       display: none;
       z-index: 10000;
       justify-content: center;
       align-items: center;
     }
     #circleNamePlaylistContainer {
       background: #2c2c2c;
       padding: 24px;
       border-radius: 12px;
       box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
       max-width: 90%;
       max-height: 90%;
       min-width: 320px;
     }
     #circleNamePlaylistContainer h3 {
       color: white;
       margin-top: 0;
       margin-bottom: 20px;
       font-size: 18px;
     }
   </style>
</head>
<body>

<div id="sidebar" class="leaflet-sidebar collapsed">
  <div class="leaflet-sidebar-tabs">
    <ul role="tablist">
      <li><a href="#tools" role="tab" style="color: #ccc;">☰</a></li>
    </ul>
  </div>
  <div class="leaflet-sidebar-content">
    <div class="leaflet-sidebar-pane" id="tools">
      <h1 class="leaflet-sidebar-header" style="color: white; padding: 15px;">
        ツール
        <span class="leaflet-sidebar-close" style="color: white;">×</span>
      </h1>
      <div style="padding: 16px;">
        <button id="drawCircleBtn" style="width: 100%; padding: 12px; margin-bottom: 8px; background-color: #6750A4; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
          円を描画
        </button>
        <button id="editModeBtn" style="width: 100%; padding: 12px; margin-bottom: 8px; background-color: #6750A4; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
          編集モード
        </button>
        <button id="dragModeBtn" style="width: 100%; padding: 12px; margin-bottom: 8px; background-color: #6750A4; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
          ドラッグモード
        </button>
        <button id="removeModeBtn" style="width: 100%; padding: 12px; margin-bottom: 8px; background-color: #dc3545; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
          削除モード
        </button>
        <button id="cancelModeBtn" style="width: 100%; padding: 12px; background-color: #666; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
          キャンセル
        </button>
      </div>
    </div>
  </div>
</div>

<div id="map"></div>

<!-- 円の名前とプレイリスト選択モーダル -->
<div id="circleNamePlaylistModal">
  <div id="circleNamePlaylistContainer">
    <h3>円の情報を入力</h3>
    <div style="margin-bottom: 16px;">
      <label for="circleNameInput" style="display: block; margin-bottom: 8px; color: white; font-size: 14px;">名前</label>
      <input type="text" id="circleNameInput" placeholder="円の名前を入力" style="width: 100%; padding: 12px; font-size: 16px; border: 2px solid #555; border-radius: 8px; background: #1a1a1a; color: white; box-sizing: border-box;" />
    </div>
    <div style="margin-bottom: 20px;">
      <label for="circlePlaylistSelect" style="display: block; margin-bottom: 8px; color: white; font-size: 14px;">プレイリスト</label>
      <select id="circlePlaylistSelect" style="width: 100%; padding: 12px; font-size: 16px; border: 2px solid #555; border-radius: 8px; background: #1a1a1a; color: white; cursor: pointer; box-sizing: border-box;">
        <option value="0">プレイリストなし（最新曲を再生）</option>
      </select>
    </div>
    <div id="circleNamePlaylistButtons" style="display: flex; gap: 12px;">
      <button id="circleNamePlaylistCancel" style="flex: 1; padding: 12px; background: #666; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; transition: opacity 0.2s;">キャンセル</button>
      <button id="circleNamePlaylistOk" style="flex: 1; padding: 12px; background: #4CAF50; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; transition: opacity 0.2s;">OK</button>
    </div>
  </div>
</div>

<script>

  // SharedPreferencesから保存された位置情報を取得
  let savedLat = 0;
  let savedLng = 0;
  
  if (typeof AndroidLocation !== 'undefined') {
    savedLat = AndroidLocation.getLastLat();
    savedLng = AndroidLocation.getLastLng();
  }
  
  // 保存された位置情報があればそれを使用、なければデフォルト位置（六本木）
  const defaultLat = (savedLat !== 0) ? savedLat : 35.66572;
  const defaultLng = (savedLng !== 0) ? savedLng : 139.73100;
  
  
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
  
  let marker = null;
  if (typeof defaultLat !== 'undefined' && typeof defaultLng !== 'undefined' && typeof currentLocationIcon !== 'undefined') {
    marker = L.marker([defaultLat, defaultLng], { icon: currentLocationIcon }).addTo(map);
    window.marker = marker;
  }

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
          }
        }
      }
    });
    
    // 円の中に入った瞬間（前回は外にいて、今回は中にいる）に音楽を再生
    if (isInCircle && !wasInCircle) {
      // どの円に入ったかを特定
      let playlistId = null;
      drawnItems.eachLayer(function(layer) {
        if (layer instanceof L.Circle) {
          if (isLocationInCircle(currentLocation, layer)) {
            if (layer.feature && layer.feature.properties && layer.feature.properties.playlistId) {
              playlistId = layer.feature.properties.playlistId;
            }
          }
        }
      });
      
      if (typeof AndroidLocation !== 'undefined') {
        if (playlistId) {
          AndroidLocation.playPlaylist(playlistId);
        } else {
          AndroidLocation.playLatestSong();
        }
      }
    }
    
    // 円から外れた瞬間（前回は中にいて、今回は外にいる）に音楽を停止
    if (!isInCircle && wasInCircle) {
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
    
    
    // 初回のみ地図の中心を移動
    if (!window.initialLocationSet) {
      map.setView([lat, lng], 17);
      window.initialLocationSet = true;
    }
    
    // 位置情報をSharedPreferencesに保存
    if (typeof AndroidLocation !== 'undefined') {
      AndroidLocation.saveLocation(lat, lng);
    }
  });

  // 位置情報の取得に失敗したときの処理
  map.on('locationerror', function(e) {
  });

  // リアルタイム位置追跡を開始（パフォーマンス最適化）
  map.locate({
    watch: true,
    setView: false,
    maxZoom: 17,
    enableHighAccuracy: false, // バッテリー消費を抑えるためfalseに変更
    timeout: 10000,
    maximumAge: 5000 // 5秒間キャッシュを使用してパフォーマンス向上
  });

  // Geoman Leafletの初期化
  map.pm.addControls({
    position: 'topleft',
    drawCircle: true,
    drawMarker: false,
    drawPolyline: false,
    drawRectangle: false,
    drawPolygon: false,
    drawCircleMarker: false,
    editMode: true,
    dragMode: true,
    cutPolygon: false,
    rotateMode: false,
    removalMode: true,
    pinningOption: false
  });
  
  // 描画した円を保持するFeatureGroup
  let drawnItems = new L.FeatureGroup();
  map.addLayer(drawnItems);
  map.pm.setGlobalOptions({
    layerGroup: drawnItems
  });
  
  // 現在の円
  let currentCircle = null;
  
  // 保存された円を復元
  function loadSavedCircles() {
    if (typeof AndroidLocation !== 'undefined') {
      try {
        const circlesJson = AndroidLocation.getAllCircles();
        const circles = JSON.parse(circlesJson);
        
        circles.forEach(function(circle) {
          const circleLayer = L.circle(
            [circle.latitude, circle.longitude],
            {
              radius: circle.radius,
              color: '#3388ff',
              fillColor: '#3388ff',
              fillOpacity: 0.2
            }
          );
          
          // プレイリスト名を取得
          let playlistName = "最新曲";
          if (circle.playlistId && circle.playlistId !== 0) {
            let playlists = [];
            try {
              const playlistsJson = AndroidLocation.getAllPlaylists();
              playlists = JSON.parse(playlistsJson);
              const playlist = playlists.find(function(p) { return p.id === circle.playlistId; });
              if (playlist) {
                playlistName = playlist.name;
              }
            } catch (e) {
              // Error parsing playlists
            }
          }
          
          // 円のプロパティを設定
          if (!circleLayer.feature) {
            circleLayer.feature = {};
          }
          circleLayer.feature.properties = circleLayer.feature.properties || {};
          circleLayer.feature.properties.title = circle.name;
          circleLayer.feature.properties.playlistId = circle.playlistId || null;
          circleLayer.feature.properties.circleId = circle.id;
          
          // ポップアップとツールチップを設定
          const popupContent = '<b>名前: </b>' + circle.name + '<br><b>プレイリスト: </b>' + playlistName;
          circleLayer.bindPopup(popupContent, {
            'maxWidth': '400',
            'className': 'PopupDrawCircle'
          });
          
          circleLayer.bindTooltip(circle.name, {
            permanent: true,
            direction: 'center',
            offset: [0, 0],
            className: 'draw-label',
            noWrap: true
          });
          
          drawnItems.addLayer(circleLayer);
        });
      } catch (e) {
        // Error loading saved circles
      }
    }
  }
  
  // sidebar-v2の初期化（DOM操作で直接制御）
  let sidebar = {
    open: function() {
      const sidebarEl = document.getElementById('sidebar');
      if (sidebarEl) {
        sidebarEl.classList.remove('collapsed');
      }
    },
    close: function() {
      const sidebarEl = document.getElementById('sidebar');
      if (sidebarEl) {
        sidebarEl.classList.add('collapsed');
      }
    },
    toggle: function() {
      const sidebarEl = document.getElementById('sidebar');
      if (sidebarEl) {
        sidebarEl.classList.toggle('collapsed');
      }
    }
  };
  
  // サイドバータブのクリックイベント
  const sidebarTab = document.querySelector('#sidebar .leaflet-sidebar-tabs > ul > li > a');
  if (sidebarTab) {
    sidebarTab.addEventListener('click', function(e) {
      e.preventDefault();
      sidebar.toggle();
    });
  }
  
  // サイドバーの閉じるボタンのイベント
  const sidebarClose = document.querySelector('#sidebar .leaflet-sidebar-close');
  if (sidebarClose) {
    sidebarClose.addEventListener('click', function(e) {
      e.preventDefault();
      sidebar.close();
    });
  }
  
  // 地図が完全に読み込まれた後に円を復元とサイドバーの初期化
  map.whenReady(function() {
    loadSavedCircles();
    
    // サイドバータブのクリックイベント
    setTimeout(function() {
      const sidebarTab = document.querySelector('#sidebar .leaflet-sidebar-tabs > ul > li > a');
      if (sidebarTab) {
        sidebarTab.addEventListener('click', function(e) {
          e.preventDefault();
          sidebar.toggle();
        });
      }
      
      // サイドバーの閉じるボタンのイベント
      const sidebarClose = document.querySelector('#sidebar .leaflet-sidebar-close');
      if (sidebarClose) {
        sidebarClose.addEventListener('click', function(e) {
          e.preventDefault();
          sidebar.close();
        });
      }
    }, 100);
  });
  
  // 描画モードが開始されたときの処理（マップの操作を無効化）
  map.on('pm:drawstart', function(e) {
    map.dragging.disable();
    map.touchZoom.disable();
    map.doubleClickZoom.disable();
    map.scrollWheelZoom.disable();
    map.boxZoom.disable();
    map.keyboard.disable();
    if (map.tap) map.tap.disable();
    
    // workingLayerのイベントをリッスン
    if (e.workingLayer) {
      e.workingLayer.on('pm:vertexadded', function(vertexEvent) {
      });
      
      e.workingLayer.on('pm:centerplaced', function(centerEvent) {
      });
      
      e.workingLayer.on('pm:change', function(changeEvent) {
      });
    }
  });
  
  // 描画モードが終了したときの処理（マップの操作を再有効化）
  map.on('pm:drawend', function(e) {
    map.dragging.enable();
    map.touchZoom.enable();
    map.doubleClickZoom.enable();
    map.scrollWheelZoom.enable();
    map.boxZoom.enable();
    map.keyboard.enable();
    if (map.tap) map.tap.enable();
  });
  
  // 描画モードが無効化されたときの処理（マップの操作を再有効化）
  map.on('pm:disable', function(e) {
    map.dragging.enable();
    map.touchZoom.enable();
    map.doubleClickZoom.enable();
    map.scrollWheelZoom.enable();
    map.boxZoom.enable();
    map.keyboard.enable();
    if (map.tap) map.tap.enable();
  });
  
  // sidebar-v2のボタンイベント
  document.getElementById('drawCircleBtn').addEventListener('click', function() {
    map.pm.enableDraw('Circle', {
      continueDrawing: false,
      snappable: false
    });
    sidebar.close();
  });
  
  document.getElementById('editModeBtn').addEventListener('click', function() {
    map.pm.toggleGlobalEditMode();
    sidebar.close();
  });
  
  document.getElementById('dragModeBtn').addEventListener('click', function() {
    map.pm.toggleGlobalDragMode();
    sidebar.close();
  });
  
  document.getElementById('removeModeBtn').addEventListener('click', function() {
    map.pm.toggleGlobalRemovalMode();
    sidebar.close();
  });
  
  document.getElementById('cancelModeBtn').addEventListener('click', function() {
    map.pm.disableDraw();
    map.pm.disableGlobalEditMode();
    map.pm.disableGlobalDragMode();
    map.pm.disableGlobalRemovalMode();
    sidebar.close();
  });

  // 円の名前とプレイリスト選択モーダルを表示する関数
  function showCircleNamePlaylistSelector(callback) {
    const modal = document.getElementById('circleNamePlaylistModal');
    const nameInput = document.getElementById('circleNameInput');
    const playlistSelect = document.getElementById('circlePlaylistSelect');
    const okButton = document.getElementById('circleNamePlaylistOk');
    const cancelButton = document.getElementById('circleNamePlaylistCancel');
    
    if (!modal || !nameInput || !playlistSelect || !okButton || !cancelButton) {
      return;
    }
    
    // 名前入力フィールドをクリア
    nameInput.value = 'デフォルト';
    
    // プレイリスト一覧を取得
    let playlists = [];
    if (typeof AndroidLocation !== 'undefined') {
      const playlistsJson = AndroidLocation.getAllPlaylists();
      try {
        playlists = JSON.parse(playlistsJson);
      } catch (e) {
        // Error parsing playlists
      }
    }
    
    // セレクトボックスをクリアしてオプションを追加
    playlistSelect.innerHTML = '<option value="0">プレイリストなし（最新曲を再生）</option>';
    playlists.forEach(function(playlist) {
      const option = document.createElement('option');
      option.value = playlist.id;
      option.textContent = playlist.name;
      playlistSelect.appendChild(option);
    });
    
    // モーダルを表示
    modal.style.display = 'flex';
    modal.style.zIndex = '10000';
    modal.style.position = 'fixed';
    modal.style.top = '0';
    modal.style.left = '0';
    modal.style.width = '100%';
    modal.style.height = '100%';
    modal.style.backgroundColor = 'rgba(0, 0, 0, 0.7)';
    modal.style.justifyContent = 'center';
    modal.style.alignItems = 'center';
    
    // 名前入力フィールドにフォーカス
    setTimeout(function() {
      nameInput.focus();
      nameInput.select();
    }, 100);
    
    // 既存のイベントリスナーを削除するためのフラグ
    let handlersAttached = false;
    
    // OKボタンの処理
    const okHandler = function() {
      if (!handlersAttached) return;
      const circleName = nameInput.value.trim();
      const selectedValue = playlistSelect.value;
      
      if (!circleName) {
        alert('名前を入力してください');
        return;
      }
      
      modal.style.display = 'none';
      okButton.removeEventListener('click', okHandler);
      cancelButton.removeEventListener('click', cancelHandler);
      document.removeEventListener('keydown', keyHandler);
      modal.removeEventListener('click', modalClickHandler);
      handlersAttached = false;
      
      const selectedPlaylistId = (selectedValue === "0" || selectedValue === null) ? null : parseInt(selectedValue);
      callback(circleName, selectedPlaylistId);
    };
    
    // キャンセルボタンの処理
    const cancelHandler = function() {
      if (!handlersAttached) return;
      modal.style.display = 'none';
      okButton.removeEventListener('click', okHandler);
      cancelButton.removeEventListener('click', cancelHandler);
      document.removeEventListener('keydown', keyHandler);
      modal.removeEventListener('click', modalClickHandler);
      handlersAttached = false;
      callback(null, null);
    };
    
    // EnterキーでOK、Escapeキーでキャンセル
    const keyHandler = function(e) {
      if (e.key === 'Enter' && e.target !== nameInput && e.target !== playlistSelect) {
        e.preventDefault();
        okHandler();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        cancelHandler();
      }
    };
    
    // モーダル外をクリックしたら閉じる
    const modalClickHandler = function(e) {
      if (e.target === modal) {
        cancelHandler();
      }
    };
    
    // イベントリスナーを追加
    okButton.addEventListener('click', okHandler);
    cancelButton.addEventListener('click', cancelHandler);
    document.addEventListener('keydown', keyHandler);
    modal.addEventListener('click', modalClickHandler);
    handlersAttached = true;
  }
  
  // Geoman Leaflet: 円が描画されたときの処理
  map.on('pm:create', function(e) {
    const layer = e.layer;
    
    if (layer instanceof L.Circle) {
      currentCircle = layer;
      
      // 描画モードを無効化
      map.pm.disableDraw();
      
      // 名前とプレイリスト選択モーダルをすぐに表示
      if (typeof showCircleNamePlaylistSelector === 'function') {
        showCircleNamePlaylistSelector(function(circleName, selectedPlaylistId) {
          // キャンセルされた場合は円を削除
          if (circleName === null || (selectedPlaylistId === null && circleName === null)) {
            drawnItems.removeLayer(layer);
            return;
          }
          
          // プレイリスト名を取得
          let playlists = [];
          if (typeof AndroidLocation !== 'undefined') {
            const playlistsJson = AndroidLocation.getAllPlaylists();
            try {
              playlists = JSON.parse(playlistsJson);
            } catch (e) {
              // Error parsing playlists
            }
          }
          
          const playlistName = selectedPlaylistId ? 
            (playlists.find(function(p) { return p.id === selectedPlaylistId; })?.name || "不明") : 
            "最新曲";
          
          if (!layer.feature) {
            layer.feature = {};
          }
          layer.feature.properties = layer.feature.properties || {};
          layer.feature.properties.title = circleName;
          layer.feature.properties.playlistId = selectedPlaylistId;
          
          // データベースに円を保存
          const circleCenter = layer.getLatLng();
          const circleRadius = layer.getRadius();
          let savedCircleId = null;
          if (typeof AndroidLocation !== 'undefined') {
            try {
              savedCircleId = AndroidLocation.saveCircle(
                circleName,
                circleCenter.lat,
                circleCenter.lng,
                circleRadius,
                selectedPlaylistId || 0
              );
              if (savedCircleId > 0) {
                layer.feature.properties.circleId = savedCircleId;
              }
            } catch (e) {
              // Error saving circle
            }
          }
          
          const popupContent = '<b>名前: </b>' + circleName + '<br><b>プレイリスト: </b>' + playlistName;
          layer.bindPopup(popupContent, {
            'maxWidth': '400',
            'className': 'PopupDrawCircle'
          });
          
          layer.bindTooltip(circleName, {
            permanent: true,
            direction: 'center',
            offset: [0, 0],
            className: 'draw-label',
            noWrap: true
          });
        });
      }
    }
  });
  
  // Geoman Leaflet: 円が削除されたときの処理
  map.on('pm:remove', function(e) {
    const layer = e.layer;
    if (layer === currentCircle) {
      currentCircle = null;
    }
    // データベースから円を削除
    if (layer.feature && layer.feature.properties && layer.feature.properties.circleId) {
      const circleId = layer.feature.properties.circleId;
      if (typeof AndroidLocation !== 'undefined') {
        AndroidLocation.deleteCircle(circleId);
      }
    }
  });
  
  // Geoman Leaflet: 円が編集されたときの処理（位置やサイズ変更）
  map.on('pm:edit', function(e) {
    const layer = e.layer;
    if (layer instanceof L.Circle && layer.feature && layer.feature.properties && layer.feature.properties.circleId) {
      const circleCenter = layer.getLatLng();
      const circleRadius = layer.getRadius();
      const circleId = layer.feature.properties.circleId;
      
      // データベースの円を更新
      if (typeof AndroidLocation !== 'undefined') {
        try {
          AndroidLocation.updateCircle(
            circleId,
            layer.feature.properties.title || "デフォルト",
            circleCenter.lat,
            circleCenter.lng,
            circleRadius
          );
        } catch (e) {
          // Error updating circle
        }
      }
    }
  });

</script>

</body>
</html>
""".trimIndent()


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapTab() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    // 位置情報の権限をリクエスト
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    // サービスを起動/停止する
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            // 権限が許可されたらバックグラウンド監視サービスを起動
            try {
                val intent = Intent(context, LocationMonitoringService::class.java).apply {
                    action = LocationMonitoringService.ACTION_START_MONITORING
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                }
                android.util.Log.d("MapTab", "Started location monitoring service")
            } catch (e: Exception) {
                android.util.Log.e("MapTab", "Failed to start location monitoring service", e)
            }
        } else {
            android.util.Log.d("MapTab", "Location permissions not granted yet")
        }
    }
    
    // コンポーネントが破棄されたときにサービスを停止
    DisposableEffect(Unit) {
        onDispose {
            // 注: ここでは停止しない（他の画面でも必要かもしれないため）
            // 必要に応じて設定で有効/無効を切り替える
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
    
    // WebViewの参照を保持
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
            WebView(ctx).apply {
                // ハードウェアアクセラレーションを有効化（レイヤータイプを設定）
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setGeolocationEnabled(true)
                    
                    // キャッシュを有効化してパフォーマンスを向上
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    
                    // 画像の自動読み込みを有効化
                    loadsImagesAutomatically = true
                    blockNetworkImage = false
                    blockNetworkLoads = false
                    
                    // メディアの自動再生を許可（必要に応じて）
                    mediaPlaybackRequiresUserGesture = false
                    
                    // ミキサーの設定
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
    
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        // 本番環境ではログを削減
                        // android.util.Log.d(
                        //     "WebView",
                        //     "[${msg.lineNumber()}] ${msg.message()}"
                        // )
                        return true
                    }
                    
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }
                    
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        // 進捗をログに出力（デバッグ用）
                        // android.util.Log.d("WebView", "Loading progress: $newProgress%")
                    }
                }
    
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // カラーテーマの色をCSS変数として設定
                        // Compose Colorを16進数に変換
                        fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
                            val r = (color.red * 255).toInt()
                            val g = (color.green * 255).toInt()
                            val b = (color.blue * 255).toInt()
                            return String.format("#%02X%02X%02X", r, g, b)
                        }
                        
                        val fabBackground = colorToHex(colorScheme.primary)
                        val fabOnBackground = colorToHex(colorScheme.onPrimary)
                        // ホバー色はprimaryContainerまたは少し明るくしたprimary
                        val fabHover = colorToHex(colorScheme.primaryContainer)
                        
                        view.evaluateJavascript(
                            """
                            (function() {
                              const root = document.documentElement;
                              root.style.setProperty('--fab-background', '$fabBackground');
                              root.style.setProperty('--fab-on-background', '$fabOnBackground');
                              root.style.setProperty('--fab-hover', '$fabHover');
                            })();
                            """.trimIndent(),
                            null
                        )
                        
                        // invalidateSizeは一度だけ実行（不要な呼び出しを削減）
                        view.postDelayed({
                            view.evaluateJavascript(
                                """
                                if (typeof window.map !== 'undefined' && window.map && typeof window.map.invalidateSize === 'function') {
                                  window.map.invalidateSize();
                                }
                                """.trimIndent(),
                                null
                            )
                        }, 100)
                    }
                    
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // ページ読み込み開始時の処理（必要に応じて）
                    }
                    
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        // リソースの読み込みを最適化（必要に応じてキャッシュを確認）
                        return super.shouldInterceptRequest(view, request)
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
                        if (typeof window.map !== 'undefined' && window.map && typeof window.map.invalidateSize === 'function') {
                          window.map.invalidateSize();
                        }
                        """.trimIndent(),
                        null
                    )
                }
            }.also { webView = it }
        },
        update = { view ->
            // カラーテーマの色をCSS変数として設定
            // Compose Colorを16進数に変換
            fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
                val r = (color.red * 255).toInt()
                val g = (color.green * 255).toInt()
                val b = (color.blue * 255).toInt()
                return String.format("#%02X%02X%02X", r, g, b)
            }
            
            val fabBackground = colorToHex(colorScheme.primary)
            val fabOnBackground = colorToHex(colorScheme.onPrimary)
            // ホバー色はprimaryContainerまたは少し明るくしたprimary
            val fabHover = colorToHex(colorScheme.primaryContainer)
            
            view.evaluateJavascript(
                """
                (function() {
                  const root = document.documentElement;
                  root.style.setProperty('--fab-background', '$fabBackground');
                  root.style.setProperty('--fab-on-background', '$fabOnBackground');
                  root.style.setProperty('--fab-hover', '$fabHover');
                })();
                """.trimIndent(),
                null
            )
        }
        )
        
        // カラーテーマが変更されたときに色を更新
        LaunchedEffect(colorScheme.primary, colorScheme.onPrimary, colorScheme.primaryContainer) {
            webView?.let { view ->
                // Compose Colorを16進数に変換
                fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
                    val r = (color.red * 255).toInt()
                    val g = (color.green * 255).toInt()
                    val b = (color.blue * 255).toInt()
                    return String.format("#%02X%02X%02X", r, g, b)
                }
                
                val fabBackground = colorToHex(colorScheme.primary)
                val fabOnBackground = colorToHex(colorScheme.onPrimary)
                val fabHover = colorToHex(colorScheme.primaryContainer)
                
                view.evaluateJavascript(
                    """
                    (function() {
                      const root = document.documentElement;
                      root.style.setProperty('--fab-background', '$fabBackground');
                      root.style.setProperty('--fab-on-background', '$fabOnBackground');
                      root.style.setProperty('--fab-hover', '$fabHover');
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
        
        // 現在地に戻るボタン
        FloatingActionButton(
            onClick = {
                webView?.evaluateJavascript(
                    """
                    if (window.map && typeof window.map.setView === 'function' && window.marker && typeof window.marker.getLatLng === 'function') {
                      // マーカーの位置を取得（現在地）
                      const markerLatLng = window.marker.getLatLng();
                      if (markerLatLng) {
                        window.map.setView([markerLatLng.lat, markerLatLng.lng], 17);
                      } else {
                        // マーカーの位置が取得できない場合は、位置情報を再取得
                        if (typeof window.map.locate === 'function') {
                          window.map.locate({
                            setView: true,
                            maxZoom: 17,
                            enableHighAccuracy: true,
                            timeout: 10000,
                            maximumAge: 0
                          });
                        }
                      }
                    } else {
                      // マップがまだ初期化されていない場合は、位置情報を再取得
                      if (window.map && typeof window.map.locate === 'function') {
                        window.map.locate({
                          setView: true,
                          maxZoom: 17,
                          enableHighAccuracy: true,
                          timeout: 10000,
                          maximumAge: 0
                        });
                      }
                    }
                    """.trimIndent(),
                    null
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "現在地に戻る"
            )
        }
    }
}

class LocationSaveInterface(private val context: Context) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicRepository = AppDatabaseModule.getMusicRepository(context)
    private val playlistRepository = AppDatabaseModule.getPlaylistRepository(context)
    private val locationCircleRepository = AppDatabaseModule.getLocationCircleRepository(context)
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
                android.util.Log.d("LocationSave", "Stopping music and clearing playlist mode")
                playerManager.pause()
                playerManager.clearPlaylistMode()
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error stopping music", e)
            }
        }
    }
    
    @JavascriptInterface
    fun getAllPlaylists(): String {
        return try {
            val playlists = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                playlistRepository.getAllPlaylists().first()
            }
            val playlistsJson = playlists.map { 
                """{"id":${it.id},"name":"${it.name}"}"""
            }.joinToString(",", "[", "]")
            android.util.Log.d("LocationSave", "Returning playlists: $playlistsJson")
            playlistsJson
        } catch (e: Exception) {
            android.util.Log.e("LocationSave", "Error getting playlists", e)
            "[]"
        }
    }
    
    @JavascriptInterface
    fun playPlaylist(playlistId: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                android.util.Log.d("LocationSave", "Playing playlist: $playlistId")
                playerManager.playPlaylist(playlistId)
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error playing playlist", e)
            }
        }
    }
    
    @JavascriptInterface
    fun saveCircle(name: String, latitude: Double, longitude: Double, radius: Double, playlistId: Long): Long {
        return try {
            android.util.Log.d("LocationSave", "saveCircle called: name=$name, lat=$latitude, lng=$longitude, radius=$radius, playlistId=$playlistId")
            val circleId = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                locationCircleRepository.createCircle(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    radius = radius,
                    playlistId = if (playlistId == 0L) null else playlistId
                )
            }
            android.util.Log.d("LocationSave", "Successfully saved circle: $name (id: $circleId)")
            circleId
        } catch (e: Exception) {
            android.util.Log.e("LocationSave", "Error saving circle: $name", e)
            e.printStackTrace()
            -1L
        }
    }
    
    @JavascriptInterface
    fun deleteCircle(circleId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                locationCircleRepository.deleteCircleById(circleId)
                android.util.Log.d("LocationSave", "Deleted circle: $circleId")
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error deleting circle", e)
            }
        }
    }
    
    @JavascriptInterface
    fun updateCircle(circleId: Long, name: String, latitude: Double, longitude: Double, radius: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val circle = locationCircleRepository.getCircleById(circleId)
                if (circle != null) {
                    val updatedCircle = circle.copy(
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        radius = radius,
                        dateModified = System.currentTimeMillis()
                    )
                    locationCircleRepository.updateCircle(updatedCircle)
                    android.util.Log.d("LocationSave", "Updated circle: $circleId")
                } else {
                    android.util.Log.w("LocationSave", "Circle not found: $circleId")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocationSave", "Error updating circle: $circleId", e)
            }
        }
    }
    
    @JavascriptInterface
    fun getAllCircles(): String {
        return try {
            val circles = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                locationCircleRepository.getAllCirclesSync()
            }
            val circlesJson = circles.map { circle ->
                """{"id":${circle.id},"name":"${circle.name.replace("\"", "\\\"")}","latitude":${circle.latitude},"longitude":${circle.longitude},"radius":${circle.radius},"playlistId":${circle.playlistId ?: 0}}"""
            }.joinToString(",", "[", "]")
            android.util.Log.d("LocationSave", "Returning ${circles.size} circles: $circlesJson")
            circlesJson
        } catch (e: Exception) {
            android.util.Log.e("LocationSave", "Error getting circles", e)
            "[]"
        }
    }
}