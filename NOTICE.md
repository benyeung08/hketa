# 授權與資料來源告知

## 關於 hkbus.app

`hkbus.app` 由 **hkbus / The Organizers Limited** 維護，前端開源於
`github.com/hkbus/hk-independent-bus-eta`，授權為 **GPL-3.0**，官方 README 明確寫出
「You can clone the project and host it by your own, for personal use」[citation:7][citation:13]。
相關元件還包含 `hk-bus-eta`（ETA 邏輯，TypeScript）、`hk-bus-crawling`（每日路線／收費爬蟲）、
`mobile-bus-app`、`route-waypoints` 等[citation:6][citation:9]。

本專案**沒有**複製上述任何程式碼，也沒有內嵌 `hkbus.app` 網頁，而是：

1. 自行撰寫 HTML / JavaScript UI；
2. 直接對接 `data.gov.hk` 系列公開 ETA 接口（與 hketa 相同來源）；
3. 由 Kotlin 層發出 HTTPS 請求。

若你把 `MainActivity.kt` 的 `LOAD_MODE` 改為 `REMOTE`、或把 hkbus 的前端放進 `web/`，
就等於分發 GPL-3.0 程式碼：請保留原授權與版權宣告、以 GPL-3.0 釋出你的衍生作品，
並留意 `hkbus.app` 名稱與商標不要拿來當自己的 App 名稱。

## 資料來源

到站預報、路線與車站資料來自香港政府「資料一站通」(DATA.GOV.HK) 及各營辦商公開接口，
資料的正確性與可用性以官方當下為準[citation:3]。本專案不收集、不上傳任何個人資料；
定位只用於即時排序附近車站。

## 與 hketa 的關係

`benyeung08/hketa` 是另一個獨立專案（Kotlin + Jetpack Compose 原生重寫，無 WebView）。
本專案只參考其功能清單與官方接口對照，未使用其原始碼；若你要沿用它的 `rail.json`
完整站表，請依該倉庫本身的授權條款處理。
