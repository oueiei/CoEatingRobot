---
marp: true
theme: default
paginate: true
backgroundColor: #ffffff
color: #2c3e50
style: |
  section {
    font-family: 'Segoe UI', 'PingFang TC', 'Microsoft YaHei', sans-serif;
    font-size: 28px;
    line-height: 1.6;
    padding: 60px;
  }
  h1 {
    color: #2c3e50;
    text-align: center;
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 40px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    text-shadow: 0 2px 4px rgba(0,0,0,0.1);
  }
  h2 {
    color: #3498db;
    font-size: 36px;
    font-weight: 600;
    border-left: 6px solid #3498db;
    padding-left: 20px;
    margin: 30px 0 20px 0;
    background: linear-gradient(90deg, rgba(52,152,219,0.1) 0%, rgba(255,255,255,0) 100%);
    padding: 15px 0 15px 20px;
    border-radius: 0 8px 8px 0;
  }
  h3 {
    color: #27ae60;
    font-size: 30px;
    font-weight: 500;
    margin: 20px 0 15px 0;
  }
  .highlight {
    background: linear-gradient(135deg, #fff3cd 0%, #ffeaa7 100%);
    padding: 20px;
    border-radius: 12px;
    border-left: 5px solid #fdcb6e;
    margin: 20px 0;
    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
  }
  .method-box {
    background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
    padding: 25px;
    border-radius: 12px;
    margin: 20px 0;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    border: 1px solid #90caf9;
  }
  .info-card {
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
    padding: 25px;
    border-radius: 15px;
    margin: 20px 0;
    box-shadow: 0 6px 20px rgba(0,0,0,0.1);
    border: 2px solid #dee2e6;
  }
  .columns {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 2rem;
    margin: 20px 0;
  }
  .tricolumns {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1.5rem;
    margin: 20px 0;
  }
  .accent-text {
    color: #e74c3c;
    font-weight: 600;
  }
  .subtitle {
    color: #7f8c8d;
    font-size: 22px;
    text-align: center;
    margin-top: -20px;
    margin-bottom: 40px;
  }
  ul, ol {
    margin: 15px 0;
    padding-left: 30px;
  }
  li {
    margin: 10px 0;
    line-height: 1.5;
  }
  strong {
    color: #2c3e50;
    font-weight: 600;
  }
  code {
    background: #f0f0f0;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 0.9em;
  }
  pre code {
    background: none;
    padding: 0;
  }
---

# 03 - Audio WebSocket

<div class="subtitle">即時語音串流 —— 從 HTTP 升級為 WebSocket</div>

<div class="info-card">

**本節重點**

- 理解 HTTP 與 WebSocket 的差異
- 認識即時雙向通訊（Full-Duplex）
- 了解音訊格式與串流原理
- 學會 Android 上的 WebSocket 連線

</div>

---

## HTTP vs WebSocket

<div class="columns">

<div class="method-box">

### HTTP（之前的做法）

```
Client → 請求 → Server
Client ← 回應 ← Server
（連線結束）
```

- 一問一答
- 每次都要重新連線
- 適合：聊天文字、取得資料

</div>

<div class="method-box">

### WebSocket（本節）

```
Client ↔ Server
（持續連線、雙向傳輸）
```

- 建立一次，持續通訊
- 雙方都能主動傳資料
- 適合：語音串流、即時通知

</div>

</div>

> 就像打電話：HTTP 是寄信（一來一回），WebSocket 是通話（隨時都能說話）

---

## 為什麼語音需要 WebSocket？

<div class="info-card">

語音資料的特性：

| 特性 | 說明 |
|------|------|
| **持續性** | 語音是連續的，不能等一句話說完才傳 |
| **即時性** | 延遲超過 200ms 就會感覺不自然 |
| **雙向性** | 你說話時，機器人也可能在回應 |
| **量大** | 每秒 24000 個取樣點 x 16 bit = 384 Kbps |

</div>

HTTP 的「一問一答」模式無法滿足這些需求！

---

## 專案架構

```
03_audio_websocket/
└── frontend/               ← Android 專案
    └── app/src/main/java/com/example/xiao2/
        ├── MainActivity.java
        ├── viewmodel/RobotViewModel.java
        ├── repository/DataRepository.java
        ├── util/
        │   ├── WebSocketHandler.java     ← WebSocket 連線管理
        │   └── CustomAudioManager.java   ← 音訊錄製與播放
        └── fragment/SettingFragment.java  ← 伺服器設定
```

<div class="highlight">

後端為獨立的 WebSocket 伺服器（含語音辨識與合成），本節重點在前端如何連接。

</div>

---

## WebSocket 連線流程

```
1. 建立連線
   App → ws://伺服器IP:8765 → Server

2. 登入（JSON 訊息）
   App → {"type": "login", "userId": "...", ...} → Server

3. 雙向音訊串流
   App ⇄ Base64 編碼的音訊資料 ⇄ Server

4. 斷線重連（自動）
   失敗 → 等 5 秒 → 重試（最多 5 次）
```

---

## 核心元件：WebSocketHandler

### 連線管理

```java
private static final String DEFAULT_SERVER_HOST = "140.112.14.225";
private static final int DEFAULT_WS_PORT = 8765;

// 建立 WebSocket 連線
public void connect(String host, int port) {
    String url = "ws://" + host + ":" + port;
    // OkHttp WebSocket client
    webSocket = client.newWebSocket(request, listener);
}
```

<div class="highlight">

**重要**：在 App 設定頁面修改 `host` 和 `port`，連到你的伺服器。

</div>

---

## 核心元件：CustomAudioManager

### 音訊參數

```java
private static final int SAMPLE_RATE = 24000;   // 24kHz 取樣率
private static final int CHANNEL = MONO;         // 單聲道
private static final int ENCODING = PCM_16BIT;   // 16-bit 精度
private static final int CHUNK_SIZE = 1024;       // 每次傳送 1024 frames
```

<div class="columns">

<div class="info-card">

**錄音流程**
1. 開啟麥克風
2. 偵測是否有人說話（VAD）
3. 將音訊切成 1024 frame 的小塊
4. Base64 編碼後透過 WebSocket 送出

</div>

<div class="info-card">

**播放流程**
1. 從 WebSocket 接收 Base64 資料
2. 解碼為 PCM 音訊
3. 透過 AudioTrack 播放
4. 處理回音消除

</div>

</div>

---

## 語音活動偵測（VAD）

```java
// 計算音訊能量
double energy = 0;
for (short sample : audioBuffer) {
    energy += sample * sample;
}
energy = Math.sqrt(energy / bufferSize);

// 判斷是否有人說話
if (energy > THRESHOLD) {
    // 有語音 → 傳送資料
} else {
    // 靜音 → 不傳送，節省頻寬
}
```

<div class="highlight">

VAD 可以過濾環境噪音，只在有人說話時才傳送資料。
調整 `THRESHOLD` 可以改變靈敏度。

</div>

---

## MVVM 資料流

```
View（Fragment / Activity）
  ↕ 觀察 LiveData
ViewModel（RobotViewModel）
  ↕ 呼叫方法
Repository（DataRepository）
  ↕ 管理連線
WebSocketHandler ⇄ 遠端伺服器
CustomAudioManager ⇄ 麥克風 / 喇叭
```

<div class="info-card">

和 Project 02 相同的 MVVM 架構，只是把 `HttpHandler` 換成了 `WebSocketHandler` + `CustomAudioManager`。

</div>

---

## 實作步驟一：設定 App

### 1. 用 Android Studio 開啟 `frontend/` 資料夾

### 2. 在設定頁面填入伺服器資訊

- **Server Host**：伺服器 IP（如 `140.112.14.225`）
- **WS Port**：WebSocket 埠號（如 `8765`）
- **User ID / Name**：你的識別名稱
- **Channel**：頻道名稱

### 3. 建置並安裝到手機

---

## 實作步驟二：測試語音

1. 確認伺服器已啟動
2. 在 App 設定頁面填入正確的伺服器資訊
3. 點擊連線按鈕
4. 對著手機說話，觀察：
   - 終端機上是否有 log 顯示收到音訊
   - 機器人是否有語音回覆

<div class="highlight">

**除錯技巧**
- 確認麥克風權限已授予
- 確認手機和伺服器在同一個網路
- 觀察 Logcat 中的 WebSocket 連線狀態

</div>

---

## HTTP vs WebSocket 總結

| 比較項目 | HTTP | WebSocket |
|---------|------|-----------|
| 連線方式 | 每次請求都建立新連線 | 建立一次，持續使用 |
| 通訊方向 | 單向（Client → Server） | 雙向（互相傳送） |
| 延遲 | 較高（每次都要握手） | 極低（已建立連線） |
| 適用場景 | 網頁瀏覽、API 查詢 | 即時語音、聊天室、遊戲 |
| 資料格式 | JSON / Form | 任意（文字 / 二進位） |

---

## 重點回顧

<div class="tricolumns">

<div class="info-card">

**WebSocket**

持續連線、雙向通訊，適合即時資料傳輸

</div>

<div class="info-card">

**音訊串流**

24kHz PCM 取樣，Base64 編碼後傳輸

</div>

<div class="info-card">

**VAD**

語音活動偵測，過濾噪音、節省頻寬

</div>

</div>

> 下一節：從音訊升級為 **影像串流**，進入 WebRTC 的世界！
