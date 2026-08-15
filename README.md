# 世界迷霧 Fog of World

> 戰爭結束了，留下的是一場不散的大霧。
> 地圖失效、道路被遺忘，世界只剩下你腳下這一小塊光。
> **唯一能撥開迷霧的方法，是親自走過去。**

一個步行探索 App：整個世界被一層戰後迷霧蓋住，你走過的地方會被永久地撥開，
留下屬於自己的世界地圖。走得越多，階級越高，解鎖成就、蓋滿世界護照。

有兩個版本，玩法與資料模型完全相同：

| | 網頁版（PWA） | **Android 原生版** |
| --- | --- | --- |
| 位置 | 專案根目錄 | [`android/`](android/) |
| 技術 | 原生 JS + Leaflet | Kotlin + Jetpack Compose + osmdroid |
| 螢幕關掉時 | ❌ 會停止記錄 | ✅ **前景服務持續記錄** |
| 安裝 | 開網址即可，可加入主畫面 | 下載 APK |

> **想整趟走完都被記錄，就用 Android 版。** 手機瀏覽器在鎖屏或切到背景後會停止提供定位，
> 這是瀏覽器的限制，網頁版無解；原生版用前景服務加上 wake lock 解決。

![授權](https://img.shields.io/badge/license-Apache--2.0-blue) ![無需後端](https://img.shields.io/badge/backend-none-green) ![PWA](https://img.shields.io/badge/PWA-offline%20ready-purple)

## 玩法

| 動作 | 說明 |
| --- | --- |
| 🚶 **開始探索** | 開啟 GPS，走路時霧會沿著路線散開，永久保留 |
| 🏅 **成就** | 32 個成就：距離、面積、連續天數、時段、海拔、地標 |
| 🛂 **世界護照** | 123 個世界地標，走進 1 公里內自動蓋章，依大洲分類 |
| 🎯 **定位** | 回到自己的位置 |
| ⚙️ **設定** | 撥霧半徑、迷霧濃度、GPS 精度門檻、模擬模式、匯出匯入 |

**階級**依撥開的面積晉升：迷霧新兵 → 偵察兵 → 拓荒者 → 遊俠 → 製圖師 → 破霧者 → 世界行者 → 霧之領主。

## 開始使用

因為用到 ES modules 與 Service Worker，需要透過 http(s) 開啟，不能直接雙擊 `index.html`：

```bash
git clone https://github.com/kylelee0728-commits/Fog-of-world.git
cd Fog-of-world
python3 -m http.server 8000     # 或 npx http-server -p 8000
```

然後開啟 <http://localhost:8000>。

> 手機上要拿到 GPS 權限必須是 **HTTPS 或 localhost**。最簡單的方式是開啟 GitHub Pages
> （Settings → Pages → Deploy from branch），網址就會是 https，手機開啟後可「加入主畫面」當成 App 使用。

### 沒辦法出門？用模擬模式

首頁點「先用模擬模式試玩」，或在設定裡打開「模擬模式」：

- **點擊地圖**任一處，角色會自己走過去
- **方向鍵 / WASD**（電腦）或畫面右下的方向鈕（手機）手動移動
- 模擬速度可在設定調整

模擬資料存在**獨立的存檔**（`demo`），不會污染真實的步行紀錄。

## Android 原生版

### 安裝

每次推上 `main`，GitHub Actions 會自動建置並更新這個 Release：

**<https://github.com/kylelee0728-commits/Fog-of-world/releases/tag/android-latest>**

手機直接點 `fog-of-world.apk` 下載安裝即可（第一次要允許「安裝未知來源的應用程式」）。
這是 debug 簽章的版本，適合自己用；要上架 Play 商店才需要另外做正式簽章。

### 自己編譯

```bash
cd android
./gradlew assembleDebug
# 產物：app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17 與 Android SDK（compileSdk 35）。

### 權限說明

| 權限 | 為什麼需要 |
| --- | --- |
| 位置（精確） | 撥霧的唯一依據 |
| 位置「一律允許」 | 螢幕關掉、切到背景時繼續記錄；不給也能用，但只在 App 開著時記錄 |
| 通知 | 前景服務的常駐通知，顯示里程與階級，可直接暫停 |
| WAKE_LOCK | 螢幕關閉後仍能收到定位回呼 |

定位用系統的 `LocationManager`，**不依賴 Google Play 服務**，沒有 GMS 的裝置也能跑。
一樣沒有後端、沒有帳號，所有資料都留在手機裡。

### 兩個版本怎麼共用資料

地標資料只有一份：`src/landmarks.js` 產生出 `android/app/src/main/assets/landmarks.json`，
CI 會檢查兩者是否一致，不一致就讓建置失敗。要改地標時改 JS 那份，然後重新產生：

```bash
node --input-type=module -e "
import { LANDMARKS } from './src/landmarks.js';
import { writeFileSync } from 'fs';
writeFileSync('android/app/src/main/assets/landmarks.json', JSON.stringify(LANDMARKS));
"
```

足跡本身兩版各自獨立儲存，目前不會互通。

## 運作方式

```
index.html ─ 介面骨架（狀態列 / 底部工具列 / 三個面板 / 開場）
styles/main.css ─ 戰後迷霧風格
vendor/leaflet/ ─ Leaflet 1.9.4（放在本地，離線也能開）
src/
├── main.js        地圖、事件接線、設定
├── fogLayer.js    迷霧圖層（Canvas 合成）
├── state.js       足跡網格、里程、成就與地標判定
├── tracker.js     GPS watchPosition／模擬行走
├── storage.js     localStorage 存檔與設定
├── landmarks.js   123 個世界地標
├── achievements.js 32 個成就與階級
├── ui.js          面板與提示訊息繪製
└── util.js        地理與網格運算
sw.js ─ 離線快取（程式碼 cache-first、地圖圖磚 stale-while-revalidate）
```

### 迷霧怎麼被撥開

1. 每個定位點量化成 **20 公尺的細網格**，重複走同一格不會重複記錄，存檔因此不會無限膨脹。
2. 兩個定位點之間距離較大時會**補上中間的格子**，高速移動不會在路上留下破洞。
3. 繪製時先在遮罩畫布上用「加成」模式畫出所有已探索的圓，再用 `destination-out`
   **一次挖穿**霧層 —— 重疊的地方不會出現接縫，邊緣是柔的。
4. 已撥霧面積用另一組 **50 公尺粗網格**統計，比「圓面積相加」準確，因為重疊只算一次。
5. 縮到遠景時自動切換成 LOD：一個索引格畫一團，避免一次畫上萬個圓。

### 里程怎麼算

以走路速度（約 1.4 m/s）每秒的位移只有 1～2 公尺，低於 GPS 雜訊門檻。
因此位移小於門檻時**不更新錨點**，讓位移慢慢累積到門檻才計入，
否則慢慢走的里程會永遠停在 0。訊號跳點（單次超過 300 公尺）則直接重設錨點不計入。

## 隱私

所有位置資料只寫入這台裝置的 `localStorage`，**沒有後端、沒有帳號、不上傳任何座標**。
地標判定與成就都在本機計算。可隨時在設定裡匯出成 JSON 備份，或清除存檔。
唯一的對外連線是 OpenStreetMap 的地圖圖磚。

## 授權

Apache-2.0。地圖資料 © OpenStreetMap 貢獻者，圖磚使用需遵守
[OSM 圖磚使用政策](https://operations.osmfoundation.org/policies/tiles/)；
若要正式上線建議改用自架或商用圖磚服務。
