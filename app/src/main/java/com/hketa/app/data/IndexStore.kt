package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/**
 * 離線索引（路線／車站／路線-車站對應）嘅讀寫。
 *
 * ## 點解要有 `Suspend` 版本
 *
 * 索引 JSON 實測約 **7 MB**（2099 條路線 / 7250 個車站 / 63150 條對應）：
 *
 * ```
 * load（readText + parse）  ≈ 187 ms
 * save（encode + write）    ≈ 342 ms
 * 單次合計（伺服 CPU）      ≈ 529 ms
 * 低階手機估計（×5）        ≈ 2.6 秒
 * ```
 *
 * 以前全部喺 **主線程**做（`viewModelScope` 預設 Dispatchers.Main），
 * 開 App 會硬生生卡住呢段時間 —— 低端機甚至接近 ANR。
 *
 * 所以新增 `loadSuspend()` / `saveSuspend()`：內部 `withContext(Dispatchers.IO)`，
 * 主線程完全唔會被阻塞。
 *
 * ## 其他改進
 *
 * - **流式讀寫**：`decodeFromStream` / `encodeToStream`，唔使先砌一個 7 MB 嘅 String
 * - **原子寫入**：寫落 `.tmp` 再 rename，寫到一半崩潰都唔會損壞原本嘅索引
 * - **單線程序列化**：`limitedParallelism(1)`，避免兩個協程同時寫同一個檔
 *
 * ## Rust 快取層（可選）
 *
 * 如果 `libhketa_store.so` 用得到（`RustIndex.available`），索引會改為
 * 寫入 **緊湊二進制**（`hketa_index.bin`）而唔係 JSON：
 *
 * ```
 * JSON  7.20 MB  →  二進制 1.04 MB（14.5%）
 * 字串去重：25770 條，讀返出嚟所有記錄共用同一個 String 實例
 * ```
 *
 * 呢個係**純加速**，唔係必要條件 —— `.so` 唔存在／加載失敗就自動行返
 * JSON 路徑，功能完全一樣。所以本地 build 冇裝 Rust toolchain 都冇問題。
 */
class IndexStore(context: Context) {

    private val file = File(context.filesDir, "hketa_index.json")

    /** Rust 二進制索引（快取；`.so` 唔喺度就唔會用） */
    private val binFile = File(context.filesDir, "hketa_index.bin")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 單線程嘅 IO dispatcher。
     *
     * 用單線程係必要嘅：索引得一個檔，如果兩個協程同時 save，
     * 兩個都寫 `.tmp` 再 rename 就會互相覆蓋。單線程就唔會有呢個問題。
     */
    private val io = Dispatchers.IO.limitedParallelism(1)

    val exists: Boolean get() = (binFile.exists() && binFile.length() > 0) ||
        (file.exists() && file.length() > 0)

    val lastModified: Long get() = maxOf(
        if (binFile.exists()) binFile.lastModified() else 0L,
        if (file.exists()) file.lastModified() else 0L
    )

    // ============ 推薦用呢兩個（唔會阻塞主線程）============

    /** 讀索引。7 MB 嘅解析會喺 IO 線程做，主線程唔會卡。 */
    suspend fun loadSuspend(): IndexData? = withContext(io) { read() }

    /** 寫索引。序列化 + 寫檔都喺 IO 線程做。 */
    suspend fun saveSuspend(data: IndexData): Unit = withContext(io) { write(data) }

    // ============ 同步版本（呼叫方要自己確保喺 IO 線程）============

    fun load(): IndexData? = read()

    fun save(data: IndexData) = write(data)

    fun clear() {
        runCatching {
            if (file.exists()) file.delete()
            if (binFile.exists()) binFile.delete()
            File(file.absolutePath + ".tmp").takeIf { it.exists() }?.delete()
            File(binFile.absolutePath + ".tmp").takeIf { it.exists() }?.delete()
        }
    }

    fun sizeKb(): Long = listOf(file, binFile)
        .filter { it.exists() }.sumOf { it.length() } / 1024

    /** 索引而家用緊邊種格式（畀 UI 顯示／除錯用） */
    val backendLabel: String
        get() = when {
            !RustIndex.available -> "Kotlin / JSON"
            binFile.exists() && binFile.length() > 0 -> "Rust / binary"
            else -> "Kotlin / JSON"
        }

    // ============ 常駐索引（Rust 完全擁有記憶體）============

    /** 二進制索引嘅路徑（畀 `RustIndex.nativeOpen` 用） */
    val binPath: String get() = binFile.absolutePath

    /**
     * 開一個 **常駐** handle：索引由 Rust 放喺 native heap，Kotlin 淨係揸住
     * 一個 `Long`。之後嘅搜尋／附近車站／路線車站全部喺 Rust 側做。
     *
     * 如果得舊 JSON、仲未轉過二進制，會喺呢度**遷移**：
     * JSON → Rust → 寫二進制 → 刪走舊 JSON（從此磁碟上淨係得二進制）。
     *
     * 失敗一律返回 0（呼叫方行返純 Kotlin 路徑）。
     */
    fun openResident(): Long {
        if (!RustIndex.available) return 0L
        return runCatching {
            if (binFile.exists() && binFile.length() > 0L) {
                RustIndex.nativeOpen(binFile.absolutePath)
            } else {
                if (!file.exists() || file.length() == 0L) return 0L
                val text = file.readText()
                val h = RustIndex.nativeOpenJson(text)
                if (h == 0L) return 0L
                if (RustIndex.nativeSaveResident(h, binFile.absolutePath)) {
                    // 遷移成功 —— 7 MB 嘅 JSON 唔使再留喺磁碟
                    runCatching { file.delete() }
                }
                h
            }
        }.getOrDefault(0L)
    }

    /** 把常駐索引寫落碟（原子寫：`.tmp` 再 rename） */
    fun saveResident(handle: Long): Boolean {
        if (handle == 0L || !RustIndex.available) return false
        return runCatching { RustIndex.nativeSaveResident(handle, binFile.absolutePath) }
            .getOrDefault(false)
    }

    // ============ 內部實作 ============

    private fun read(): IndexData? = runCatching {
        // 1) Rust 二進制優先（細 7 倍、解析快）
        readRust()?.let { return it }
        // 2) 回退：原本嘅 JSON
        if (!file.exists() || file.length() == 0L) return null
        // 流式：直接由 InputStream 解，唔會先砌一個 7 MB 嘅 String
        file.inputStream().buffered().use {
            json.decodeFromStream<IndexData>(it)
        }
    }.getOrNull()

    /** 由 Rust 二進制讀。失敗一律返回 null（呼叫方會回退 JSON）。 */
    private fun readRust(): IndexData? {
        if (!RustIndex.available) return null
        if (!binFile.exists() || binFile.length() == 0L) return null
        return runCatching {
            val h = RustIndex.nativeLoad(binFile.absolutePath)
            if (h == 0L) return null
            try {
                RustIndex.toIndexData(h)
            } finally {
                RustIndex.nativeFree(h)
            }
        }.getOrNull()
    }

    private fun write(data: IndexData) {
        // Rust 可用 → 寫二進制，然後刪走舊 JSON（唔使再佔 7 MB）
        if (RustIndex.available && writeRust(data)) {
            runCatching { if (file.exists()) file.delete() }
            return
        }
        writeJson(data)
    }

    /** Kotlin → JSON 字串 → Rust → 二進制。失敗返回 false，呼叫方行返 JSON。 */
    private fun writeRust(data: IndexData): Boolean = runCatching {
        val text = json.encodeToString(data)
        val h = RustIndex.nativeFromJson(text)
        if (h == 0L) return false
        try {
            RustIndex.nativeSave(h, binFile.absolutePath)
        } finally {
            RustIndex.nativeFree(h)
        }
    }.getOrDefault(false)

    private fun writeJson(data: IndexData) {
        runCatching {
            val tmp = File(file.absolutePath + ".tmp")
            // 流式寫入
            tmp.outputStream().buffered().use {
                json.encodeToStream(data, it)
                it.flush()
            }
            // 原子替換：寫到一半崩潰，原本嘅索引仍然完好
            if (tmp.exists() && tmp.length() > 0) {
                if (file.exists() && !file.delete()) return@runCatching
                if (!tmp.renameTo(file)) return@runCatching
            } else {
                tmp.delete()
            }
        }
    }
}
