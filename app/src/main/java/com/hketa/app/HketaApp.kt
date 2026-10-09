package com.hketa.app

import android.app.Application
import com.hketa.app.util.CrashLog

/**
 * Application 入口 —— 淨係做一件事：裝崩潰記錄。
 *
 * 裝咗之後，任何未補捉嘅異常（包括協程喺背景丟出嚟嗰啲）都會先寫落
 * `crash_log.txt` 先至交畀系統。所以就算 App 閃退，下次開到嘅時候
 * 都可以喺「關於」頁撳「複製崩潰記錄」攞到堆疊，
 * 或者去 檔案管理器 ▸ Android/data/com.hketa.app/files/crash_log.txt 直接睇。
 */
class HketaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
