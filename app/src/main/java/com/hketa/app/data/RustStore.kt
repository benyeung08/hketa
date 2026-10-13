package com.hketa.app.data

import android.content.Context
import java.io.File

/**
 * **成個 APK 嘅「記憶體與儲存」—— 由 Rust 內核擁有。**
 *
 * 以前 App 入面五種儲存各寫各嘅：
 *
 * | 嘢 | 以前點儲 |
 * |---|---|
 * | 收藏車站 | `SharedPreferences` 塞一條 JSON 大字串 |
 * | 輕鐵特別班次 | 一個 `hketa_lr_special.json` |
 * | ETA 離線快取 | 一個目錄一堆 `.json` |
 * | 班次表離線快取 | 一個目錄一堆 `.json` |
 * | Leaflet 資源包 | `filesDir/hketa_assets/*.css｜js` |
 *
 * 而家統一交畀 `rust/src/store.rs`：
 *
 * * **記憶體**：全部 KV 住喺 **native heap**，Kotlin 側一個位元組都唔使揸住
 * * **磁碟**：每個命名空間一個 `.s` 檔，**原子寫**（`.s.tmp` 再 rename）
 * * **上限**：每個命名空間 4096 條，超咗踢最舊寫入嗰條
 * * **懶載入**：命名空間第一次 `get`/`put` 先讀碟，開 App 唔會一口氣讀晒
 *
 * ## ⚠️ 最重要：一定有回退
 *
 * `.so` 唔喺度／`init` 失敗／JNI 甩 exception → [available] 變 `false`
 * → 所有方法返「空」值 → 各個 store 行返原本嘅 Kotlin 實作。
 * **功能完全一樣，只係冇咗「Rust 擁有記憶體」呢層。**
 *
 * 所以每一個 call 都包咗 `runCatching` —— JNI 甩 exception 唔可以傳出去
 * （會搞到成個 App 崩），一定要喺呢度截住。
 */
object RustStore {

    // ---------------------------------------------------------- 命名空間

    /** 收藏車站 */
    const val NS_FAVORITES = "fav"

    /** ETA 離線快取 */
    const val NS_ETA = "eta"

    /** 班次表離線快取 */
    const val NS_TIMETABLE = "tt"

    /** 輕鐵特別班次掃描結果 */
    const val NS_SPECIAL = "lrsp"

    /** Leaflet 靜態資源（CSS／JS）離線包 */
    const val NS_ASSETS = "ast"

    // ---------------------------------------------------------- 初始化

    /** `System.loadLibrary` 成功 ＋ 根目錄建到，先算可用 */
    private var rootOk = false

    val available: Boolean
        get() = rootOk && RustKernel.available && runCatching { RustKernel.storeReady() }.getOrDefault(false)

    /**
     * 叫一次（Application 或者 ViewModel 初始化嗰陣）。
     * 可以重複叫 —— 會重新指向同一個根目錄。
     */
    fun init(context: Context): Boolean {
        if (!RustKernel.available) {
            rootOk = false
            return false
        }
        val root = runCatching {
            File(context.filesDir, "hketa_store").absolutePath
        }.getOrNull() ?: run {
            rootOk = false
            return false
        }
        rootOk = runCatching { RustKernel.storeInit(root) }.getOrDefault(false)
        return rootOk
    }

    // ---------------------------------------------------------- 讀寫

    fun put(ns: String, key: String, value: String): Boolean {
        if (!available || ns.isBlank() || key.isBlank()) return false
        return runCatching {
            RustKernel.storePut(ns, key, value, System.currentTimeMillis())
        }.getOrDefault(false)
    }

    fun get(ns: String, key: String): String? {
        if (!available || ns.isBlank() || key.isBlank()) return null
        return runCatching { RustKernel.storeGet(ns, key) }.getOrNull()
    }

    /** 寫入時間（epoch ms）；冇就 0 */
    fun at(ns: String, key: String): Long {
        if (!available || ns.isBlank() || key.isBlank()) return 0L
        return runCatching { RustKernel.storeAt(ns, key) }.getOrDefault(0L)
    }

    fun del(ns: String, key: String): Boolean {
        if (!available || ns.isBlank() || key.isBlank()) return false
        return runCatching { RustKernel.storeDel(ns, key) }.getOrDefault(false)
    }

    fun keys(ns: String): List<String> {
        if (!available || ns.isBlank()) return emptyList()
        val arr = runCatching { RustKernel.storeKeys(ns) }.getOrNull() ?: return emptyList()
        return arr.toList()
    }

    fun clear(ns: String): Boolean {
        if (!available || ns.isBlank()) return false
        return runCatching { RustKernel.storeClear(ns) }.getOrDefault(false)
    }

    /** 條目數 */
    fun count(ns: String): Long {
        if (!available || ns.isBlank()) return 0L
        return runCatching { RustKernel.storeCount(ns) }.getOrDefault(0L)
    }

    /** value 嘅總位元組數 */
    fun bytes(ns: String): Long {
        if (!available || ns.isBlank()) return 0L
        return runCatching { RustKernel.storeBytes(ns) }.getOrDefault(0L)
    }

    /**
     * 把所有改動落碟。`put` 只係改記憶體 ＋ 標 dirty，所以**關鍵時機要 call 呢個**
     * （例如改完收藏）。就算唔 call，內容都喺 native heap 入面、App 期間都讀到，
     * 只係殺咗 App 之後會冇咗。
     */
    fun flush(): Boolean {
        if (!available) return false
        return runCatching { RustKernel.storeFlush() }.getOrDefault(false)
    }

    /** 已知嘅命名空間名單（設定頁顯示） */
    fun namespaces(): List<String> {
        if (!available) return emptyList()
        val arr = runCatching { RustKernel.storeNamespaces() }.getOrNull() ?: return emptyList()
        return arr.toList()
    }

    /**
     * 設定頁顯示用，例如：`Rust / store · 3 個命名空間 · 412 條 · 58 KB`。
     * 冇就返 `Kotlin / fallback`。
     */
    fun statsLine(): String {
        if (!available) return "Kotlin / fallback"
        val s = runCatching { RustKernel.storeStats() }.getOrNull()
        if (s.isNullOrBlank()) return "Kotlin / fallback"
        // Rust 側格式：ns=6 keys=1234 bytes=567890 hits=.. misses=..
        val map = s.split(" ").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        val nsCount = map["ns"] ?: "0"
        val keys = map["keys"] ?: "0"
        val bytes = map["bytes"]?.toLongOrNull() ?: 0L
        val kb = if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"
        return "Rust / store · $nsCount 個命名空間 · $keys 條 · $kb"
    }
}
