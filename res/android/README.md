# App 圖示資產（res/android）

Launcher icon 來源是你提供的彩虹巴士圖，已存成 `logo-source.png`（1080×1080）。
`config.xml` 的 `<platform name="android">` 已宣告五個密度，build 時由 cordova-android 15
自動複製進 `mipmap-*`，不需手動改 Gradle 或 AndroidManifest。

## 檔案

| 檔案 | 用途 |
| --- | --- |
| `logo-source.png` | 原始圖，1080×1080（保留來源，不要改） |
| `logo-preview.png` | 圓形 / 圓角方 / squircle / 單色主題四種外觀預覽 |
| `logo-playstore-512.png` | 512×512，Google Play 商店圖示 |
| `icons/<density>/ic_launcher.png` | legacy 方形圖示（48 / 72 / 96 / 144 / 192 px） |
| `icons/<density>/ic_launcher_background.png` | adaptive 背景：彩虹漸層（108dp × scale） |
| `icons/<density>/ic_launcher_foreground.png` | adaptive 前景：去背的巴士＋司機，透明底 |
| `icons/<density>/ic_launcher_monochrome.png` | Android 13+ 主題圖示：白色剪影 |
| `../../www/img/app-logo.png` | App 內（頁首／設定頁）顯示的 256px logo |

密度對照：`mdpi` 1×、`hdpi` 1.5×、`xhdpi` 2×、`xxhdpi` 3×、`xxxhdpi` 4×。
adaptive 畫布固定 108dp，所以背景／前景／單色依序是 108 / 162 / 216 / 324 / 432 px。

## 為什麼這樣處理

原始圖是彩虹漸層 + 白色巴士 + 黃色司機的**完整正方形構圖**。直接拿去當 adaptive 前景，
會被各家 OEM 的圓形／方形遮罩裁掉邊緣，所以拆成兩層：

1. **背景**：取原圖頂端無主體的 70px 逐欄中位數，重建乾淨的水平彩虹漸層（不含巴士）。
2. **前景**：用「與重建背景的色差」抓出主體，形態學清理 + 只保留最大連通成分 + 邊緣羽化，
   裁緊後縮到畫布高度的 78% 置中，四角透明。
3. **單色**：把前景的 alpha 轉成純白剪影，讓 Android 13+ 跟隨系統配色。
4. **legacy / Play / App 內**：背景 + 前景合成，主體高度 86%，不透明。

`minSdk=26` 代表所有支援裝置都是 Android 8+，都會拿到 adaptive icon；`src` 只是 fallback。

## 重產

資產已隨專案附上，一般 `cordova build` **不需要 Python**。要換圖或調比例時：

```bash
# 把新圖覆蓋成 res/android/logo-source.png（務必正方形）
python3 scripts/generate-icons.py
```

需要 `Pillow`、`numpy`、`opencv-python`。可調常數在腳本開頭：
`FOREGROUND_RATIO`（adaptive 前景大小）、`LEGACY_RATIO`、`DIFF_THRESHOLD`（去背敏感度）。

> 換圖後記得同步 `logo-playstore-512.png` 與 `www/img/app-logo.png`（腳本會一起重產），
> 並到 Play Console 更新商店圖示。

## 版權

圖片由使用者提供並指定作為本 App 圖示；專案程式碼為 MIT，但**不代表圖片的授權**。
若要公開發布或上架，請確認你擁有這張圖的使用／改作權利。
