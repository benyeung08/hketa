package com.hketa.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩潰記錄。
 *
 * 安裝一個 `Thread.setDefaultUncaughtExceptionHandler`：
 *
 *   ① 把堆疊寫落 `filesDir/crash_log.txt`
 *   ② 同時寫一份去 `getExternalFilesDir(null)/crash_log.txt`
 *      —— 呢個目錄用檔案管理器／USB 可以直接睇到，
 *      就算 App 一開就閃退、入唔到設定頁都拎到記錄
 *   ③ 交返畀原本嘅 handler（等 Android 照常彈「程式已停止」）
 *
 * 保留最近 5 次，每次前面有時間、版本、裝置、Android 版本。
 */
object CrashLog {

    private const val NAME = "crash_log.txt"
    private const val MAX_KEEP = 5

    @Volatile
    private var installed = false

    @Volatile
    private var dir: File? = null

    @Volatile
    private var externalDir: File? = null

    fun install(context: Context) {
        if (installed) return
        installed = true
        dir = context.filesDir
        externalDir = runCatching { context.getExternalFilesDir(null) }.getOrNull()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            // 交返畀系統，行為同平時一樣（會彈「程式已停止」），
            // 但記錄已經落咗碟，下次開 App 可以讀返出嚟
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val (vName, vCode) = runCatching {
            @Suppress("DEPRECATION")
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            val n = p.versionName ?: "?"
            val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode
            else p.versionCode.toLong()
            n to c
        }.getOrDefault("?" to 0L)

        val sb = StringBuilder()
        sb.appendLine("===== HKATE crash =====")
        sb.appendLine("time    : $stamp")
        sb.appendLine("version : $vName ($vCode)")
        sb.appendLine("device  : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("thread  : ${thread.name}")
        sb.appendLine()
        sb.appendLine(throwable.toString())
        throwable.stackTrace.take(40).forEach { sb.appendLine("    at $it") }

        var cause: Throwable? = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            sb.appendLine()
            sb.appendLine("Caused by: ${cause}")
            cause.stackTrace.take(30).forEach { sb.appendLine("    at $it") }
            cause = cause.cause
            depth++
        }
        sb.appendLine()

        val text = sb.toString()
        runCatching { File(dir, NAME).appendText(text) }
        runCatching { externalDir?.let { File(it, NAME).appendText(text) } }
        trim()
    }

    /** 淨係保留最近 [MAX_KEEP] 次，費事個檔愈滚愈大 */
    private fun trim() {
        val f = File(dir ?: return, NAME)
        if (!f.exists()) return
        val all = f.readText()
        val blocks = all.split("===== HKATE crash =====")
            .filter { it.isNotBlank() }
        if (blocks.size <= MAX_KEEP) return
        val kept = blocks.takeLast(MAX_KEEP)
            .joinToString("") { "===== HKATE crash =====$it" }
        f.writeText(kept)
    }

    /** 讀返出嚟（畀「複製崩潰記錄」掣用） */
    fun read(context: Context): String {
        val f = File(context.filesDir, NAME)
        if (!f.exists()) return ""
        return runCatching { f.readText() }.getOrDefault("")
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, NAME).delete() }
        runCatching { context.getExternalFilesDir(null)?.let { File(it, NAME).delete() } }
    }
}
