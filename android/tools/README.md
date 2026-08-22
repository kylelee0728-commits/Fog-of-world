# 圖示原始檔

App 裡的圖示全部是手畫的，不用表情符號 —— 表情符號在不同廠商的系統上長相差很多，
也沒辦法跟著主題上色。

- `icons.py`：39 個圖示的路徑資料，全部畫在 24×24 的網格上。
- `gen.py`：把路徑輸出成 `app/src/main/res/drawable/ic_*.xml`。

改完圖示後執行：

```
python3 android/tools/gen.py
```

顏色一律由使用端 tint（Compose 用 `FogIcon`，地圖上的地標用 `FogView.iconBitmap`），
所以 drawable 裡的 `fillColor` 固定是白色。
