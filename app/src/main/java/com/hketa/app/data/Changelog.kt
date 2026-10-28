package com.hketa.app.data

import com.hketa.app.util.AppLocale

/**
 * 內置版本歷史（Changelog）—— 中英雙語。
 *
 * GitHub Releases 而家仲未出過，所以呢份係「離線」紀錄 —— 唔使聯網都睇到，
 * 且唔會因為 API 失敗而顯示空白。App 入面會先顯示呢份，
 * 若然成功由 GitHub 拉到 Release，再顯示「線上版本」嗰一段。
 *
 * 每筆記錄同時提供繁中、簡中、英文三份文案，
 * 顯示時用 [forLang] 依目前介面語言挑選。
 */
object Changelog {

    /** 一筆版本紀錄（三語） */
    data class Entry(
        val version: String,
        val date: String,               // YYYY-MM-DD
        val titleZhHant: String,
        val titleZhHans: String,
        val titleEn: String,
        val itemsZhHant: List<String>,
        val itemsZhHans: List<String>,
        val itemsEn: List<String>
    )

    /** 依語言解析後嘅單語紀錄 */
    data class Resolved(
        val version: String,
        val date: String,
        val title: String,
        val items: List<String>
    )

    /** 由新到舊 */
    val entries: List<Entry> = listOf(

        Entry(
            version = "1.2.21",
            date = "2026-10-28",
            titleZhHant = "修好五個潛在錯誤：嶼巴索引並發寫入會丟資料、APK 更新校驗失敗照裝、損壞索引檔會閃退",
            titleZhHans = "修好五个潜在错误：屿巴索引并发写入会丢数据、APK 更新校验失败照装、损坏索引文件会闪退",
            titleEn = "Five latent bugs fixed: NLB index losing data to a race, APK updates installed without a checksum, corrupt index file aborting the app",
            itemsZhHant = listOf(
                "**嶼巴索引並發寫入會靜默丟資料**：重建索引嗰陣，嶼巴會一次過開幾十個協程去抓路線，而呢啲協程**寫入同一個 ArrayList**。ArrayList 嘅 add 係「讀 size、寫元素、size 加一」三步，並唔係原子操作 —— 兩個協程同時讀到同一個 size，就會寫去同一格，其中一個被靜默覆蓋，size 最後只加一。結果係嶼巴嘅**路線-車站對應會少咗**，表現出嚟就係「某啲嶼巴路線顯示唔到站」或者站序錯亂，而且唔會報錯、唔會留低痕跡。而家改為分批（每批 8 條），每個協程淨係返自己嗰份結果，全部完成之後先喺單線程入面合併 —— 冇共享可變狀態就冇競態。有意思嘅係，同一個檔案嘅城巴段本來就做啱咗（分批 + 並發安全集合 + 原子計數），係嶼巴段漏咗",
                "**APK 更新嘅 SHA-256 校驗以前係「失敗即放行」**：下載 APK 之前會先攞一個 .sha256 檔攞校驗值，但攞唔到（超時、5xx、DNS 失敗）嗰陣，舊程式碼會當「冇需要校驗」，**照樣裝落去** —— 即係校驗最需要嘅時候（網絡有問題、連線可能被攔截）反而唔校驗。而家改為三態：**.sha256 回 404**（即 CI 根本冇出呢個檔）先會降級並喺設定頁講明；**其餘任何失敗一律唔裝**，寧願今次唔更新，都唔會裝一個校驗唔到嘅 APK。呢個係少數我哋刻意選擇「失敗就停」而唔係「失敗就放行」嘅地方",
                "**損壞嘅索引檔會令 App 直接閃退，而唔係報錯**：Rust 側讀二進制索引嗰陣，有幾處會用檔案入面讀出嚟嘅數字去開一塊記憶體（例如「呢段有幾多項」）。以前呢個數字完全冇封頂 —— 一個損壞或者被截斷嘅檔可以畀出幾十億，跟手就即刻要求分配幾十 GB，系統直接 abort。abort 發生喺 native 層，**冇得被 Kotlin 嘅 try/catch 捕獲**，所以表現係「一撳就閃退」而唔係一句友善嘅錯誤訊息，用戶亦冇辦法自行修復（除非清資料）。而家全部改為按「剩餘位元組 ÷ 每項最少位元組」封頂，檔案再有問題都只會讀到邊界就停，最壞係索引睇唔到嘢，而唔係成個 App 死",
                "**同一個查詢白做咗兩次**：搵附近車站嗰陣，同一個站嘅路線定義會被呼叫兩次 —— 一次用嚟顯示、一次用嚟排序。最多八十個候選站，即係白做咗八十次查表加排序。而家改為複用第一次嘅結果。呢個唔會令結果錯，純粹係浪費，但係喺低端機上面係實實在在嘅卡頓",
                "**補咗一個 HTML／JS 注入嘅漏洞位**：地圖頁會把站名之類嘅文字塞入 JavaScript 字串入面。以前嘅跳脫函式淨係處理咗引號同反斜線，**漏咗 `</`** —— 一個以 `</script` 開頭嘅站名可以直接閉合 script 標籤，之後嘅內容會被當 HTML 執行。站名而家會由第三方聚合檔補返嚟（嗰份檔唔係我哋控制），所以有需要防呢一手。而家會把 `</` 寫成 `<\\/`，閉合唔到就注入唔到",
                "**真正支援 Android 17（API 37），唔係淨係改個數字**：compileSdk／targetSdk 一早係 37，但升 target 會帶嚟一批行為變更 —— 淨改數字唔測，結果就係「出咗包先至爆」。今次逐條對過。最要緊嘅係 **Rust 內核喺 16 KB page size 嘅裝置上會載唔入**：Android 15 起支援 16 KB 分頁、Android 17 設備普遍採用，而 ELF 嘅 LOAD 段預設淨係 4 KB 對齊，喺 16 KB 裝置上 mmap 會失敗 → System.loadLibrary 抛 UnsatisfiedLinkError → 內核**靜默回退**純 Kotlin。呢類失敗喺 CI 完全睇唔出（編譯成功、打包成功），只會喺用戶機上「時靈時唔靈」。已加 rust/.cargo/config.toml 叫 linker 用 16 KB 對齊，CI 亦加咗一步用 llvm-readelf 逐個 .so 驗 Align（要見到 0x4000），NDK 升到 r27。另外 Android 17 把「動態程式碼載入」保護擴展到 native library：.so 必須唯讀，所以設咗 extractNativeLibs=\"false\" —— 直接由 APK mmap（APK 本身唯讀）；解壓出嚟嘅 .so 權限係 755，會載唔入。最後：官方預告 usesCleartextTraffic 會被棄用，改用 Network Security Config；Android 17 起憑證透明度（CT）默認啟用，NSC 入面預留咗可以按域豁免嘅入口（預設全關）。核過唔使改嘅：Widget 冇位圖所以唔受 RemoteViews 記憶體上限影響、冇反射所以唔受 MessageQueue 無鎖化／static final 影響、冇藍牙／通訊錄／簡訊／背景音訊、冇區域網絡連線（地圖個 https://localhost/ 淨係 base URL，唔係真連線）"
                        ),
            itemsZhHans = listOf(
                "**屿巴索引并发写入会静默丢资料**：重建索引嗰阵，屿巴会一次过开几十个协程去抓路线，而呢啲协程**写入同一个 ArrayList**。ArrayList 嘅 add 系「读 size、写元素、size 加一」三步，并唔系原子操作 —— 两个协程同时读到同一个 size，就会写去同一格，其中一个被静默覆盖，size 最后只加一。结果系屿巴嘅**路线-车站对应会少咗**，表现出来就系「某啲屿巴路线显示唔到站」或者站序错乱，而且唔会报错、唔会留低痕迹。而家改为分批（每批 8 条），每个协程净系返自己嗰份结果，全部完成之后再喺单线程里面合并 —— 冇共享可变状态就冇竞态。有意思嘅系，同一个文件嘅城巴段本来就做啱咗（分批 + 并发安全集合 + 原子计数），系屿巴段漏咗",
                "**APK 更新嘅 SHA-256 校验以前系「失败即放行」**：下载 APK 之前会先攞一个 .sha256 档攞校验值，但攞唔到（超时、5xx、DNS 失败）嗰阵，旧代码会当「冇需要校验」，**照样装落去** —— 即系校验最需要嘅时候（网络有问题、连线可能被拦截）反而唔校验。而家改为三态：**.sha256 回 404**（即 CI 根本冇出呢个档）先会降级并喺设定页讲明；**其余任何失败一律唔装**，宁愿今次唔更新，都唔会装一个校验唔到嘅 APK。呢个系少数我哋刻意选择「失败就停」而唔系「失败就放行」嘅地方",
                "**损坏嘅索引文件会令 App 直接闪退，而唔系报错**：Rust 侧读二进制索引嗰阵，有几处会用文件里面读出来嘅数字去开一块内存（例如「呢段有几多项」）。以前呢个数字完全冇封顶 —— 一个损坏或者被截断嘅文件可以畀出几十亿，跟手就即刻要求分配几十 GB，系统直接 abort。abort 发生喺 native 层，**冇得被 Kotlin 嘅 try/catch 捕获**，所以表现系「一㩒就闪退」而唔系一句友善嘅错误讯息，用户亦冇办法自行修复（除非清资料）。而家全部改为按「剩余字节 ÷ 每项最少字节」封顶，文件再有问题都只会读到边界就停，最坏系索引睇唔到嘢，而唔系成个 App 死",
                "**同一个查询白做咗两次**：揾附近车站嗰阵，同一个站嘅路线定义会被呼叫两次 —— 一次用来显示、一次用来排序。最多八十个候选站，即系白做咗八十次查表加排序。而家改为复用第一次嘅结果。呢个唔会令结果错，纯粹系浪费，但系喺低端机上面系实实在在嘅卡顿",
                "**补咗一个 HTML／JS 注入嘅漏洞位**：地图页会把站名之类嘅文字塞入 JavaScript 字符串里面。以前嘅跳脱函数净系处理咗引号同反斜线，**漏咗 `</`** —— 一个以 `</script` 开头嘅站名可以直接闭合 script 标签，之后嘅内容会被当 HTML 执行。站名而家会由第三方聚合档补返嚟（嗰份档唔系我哋控制），所以有需要防呢一手。而家会把 `</` 写成 `<\\/`，闭合唔到就注入唔到",
                "**真正支援 Android 17（API 37），唔系净系改个数字**：compileSdk／targetSdk 一早系 37，但升 target 会带来一批行为变更 —— 净改数字唔测，结果就系「出了包先至爆」。今次逐条对过。最要紧嘅系 **Rust 内核喺 16 KB page size 嘅装置上会载入唔到**：Android 15 起支援 16 KB 分页、Android 17 设备普遍采用，而 ELF 嘅 LOAD 段默认净系 4 KB 对齐，喺 16 KB 装置上 mmap 会失败 → System.loadLibrary 抛 UnsatisfiedLinkError → 内核**静默回退**纯 Kotlin。呢类失败喺 CI 完全睇唔出（编译成功、打包成功），只会在用户机上「时灵时唔灵」。已加 rust/.cargo/config.toml 叫 linker 用 16 KB 对齐，CI 亦加咗一步用 llvm-readelf 逐个 .so 验 Align（要见到 0x4000），NDK 升到 r27。另外 Android 17 把「动态代码载入」保护扩展到 native library：.so 必须只读，所以设咗 extractNativeLibs=\"false\" —— 直接由 APK mmap（APK 本身只读）；解压出来嘅 .so 权限系 755，会载入唔到。最后：官方预告 usesCleartextTraffic 会被弃用，改用 Network Security Config；Android 17 起凭证透明度（CT）默认启用，NSC 里面预留咗可以按域豁免嘅入口（默认全关）。核过唔使改嘅：Widget 冇位图所以唔受 RemoteViews 内存上限影响、冇反射所以唔受 MessageQueue 无锁化／static final 影响、冇蓝牙／通讯录／简讯／背景音频、冇区域网络连线（地图个 https://localhost/ 净系 base URL，唔系真连线）"
                        ),
            itemsEn = listOf(
                "**The NLB index was writing to a shared ArrayList from many coroutines at once — and silently losing data**: while rebuilding the index, NLB fires dozens of coroutines to fetch routes, and all of them **wrote into the same ArrayList**. `ArrayList.add` is three steps (read `size`, write the slot, increment `size`) and is not atomic: two coroutines that read the same `size` write to the same slot, one entry is silently overwritten, and `size` only advances once. The effect was that NLB **route-to-stop mappings went missing** — some NLB routes would show no stops, or the stop order would be scrambled, with no error and no trace left behind. Now the work is chunked (8 routes per batch), each coroutine returns its own result, and everything is merged on a single thread afterwards: no shared mutable state means no race. The irony is that the Citybus branch of the very same file already did this correctly (chunking + a concurrent-safe set + an atomic counter); only the NLB branch was missed",
                "**The SHA-256 check on APK updates used to fail open**: before downloading an update the app fetches a `.sha256` file, but if that fetch failed (timeout, 5xx, DNS error) the old code treated it as \"nothing to verify\" and **installed anyway** — which means the check was skipped exactly when it mattered most, i.e. when the network is flaky or the connection might be tampered with. It is now a three-state decision: **a 404 on `.sha256`** (meaning CI simply never published that file) degrades gracefully with a note in Settings; **any other failure means do not install**. Skipping one update is preferable to installing an APK we cannot verify. This is one of the few places where we deliberately choose fail-closed over fail-open",
                "**A corrupt index file used to abort the whole app instead of raising an error**: when the Rust side reads the binary index, several places allocate a buffer using a count read straight out of the file (for example \"how many entries are in this section\"). Those counts had **no upper bound** — a corrupt or truncated file could claim billions and immediately ask the allocator for tens of gigabytes, and the process aborts. An abort happens in native code and **cannot be caught by Kotlin's try/catch**, so users saw an instant crash rather than a friendly message, and had no way to recover other than clearing app data. Every such count is now capped by \"bytes remaining ÷ minimum bytes per entry\", so a broken file simply stops at the boundary: worst case the index looks empty, the app does not die",
                "**One lookup was being done twice**: when searching for nearby stops, the route definitions for a given stop were computed twice — once to display and once to sort. With up to 80 candidate stops that is 80 wasted lookups plus 80 wasted sorts. The first result is now reused. This never produced a wrong result, it was pure waste, but on a low-end phone it is real jank",
                "**Closed an HTML/JS injection hole**: the map screen interpolates text such as stop names into a JavaScript string literal. The escaping helper handled quotes and backslashes but **missed `</`** — a stop name beginning with `</script` could close the script tag early, and everything after it would be treated as HTML. Stop names are now supplemented from a third-party aggregate file we do not control, so this is worth guarding. `</` is now written as `<\\/`: if the tag cannot be closed, nothing can be injected",
                "**Real Android 17 (API 37) support, not just bumping a number**: compileSdk/targetSdk were already 37, but raising target flips on a batch of behaviour changes, and bumping the number without testing them is exactly how a release turns into a crash wave. Every one was checked this round. The important one: **the Rust kernel fails to load on 16 KB page-size devices**. Android 15 introduced 16 KB pages and Android 17 devices commonly use them, but an ELF's LOAD segments are aligned to 4 KB by default, so mmap fails, System.loadLibrary throws UnsatisfiedLinkError, and the kernel **silently falls back** to pure Kotlin. CI cannot see this at all (it compiles, it packages); it only shows up as the kernel working on some phones and not others. Added rust/.cargo/config.toml telling the linker to align to 16 KB, plus a CI step that runs llvm-readelf over every .so and checks Align (must show 0x4000), and moved the NDK to r27. Also, Android 17 extends the safer dynamic code loading (DCL) protection from DEX/JAR to native libraries: a .so passed to System.load() must be read-only, so extractNativeLibs=\"false\" is now set - the .so is mmapped straight from the APK (which is read-only), whereas an extracted .so lands with mode 755 and would fail to load. Finally: usesCleartextTraffic is deprecated in favour of Network Security Config, which this release adopts; and Certificate Transparency is on by default from Android 17, so the NSC reserves a per-domain exemption hook (off by default). Confirmed as not applicable: no bitmaps, so the RemoteViews memory cap does not bite; no reflection, so the lock-free MessageQueue and immutable static final changes are irrelevant; no Bluetooth, contacts, SMS or background audio; no local-network access (the map's https://localhost/ is only a base URL, not a real connection)"
                        )
        ),
        Entry(
            version = "1.2.2",
            date = "2026-10-16",
            titleZhHant = "離線地圖瓦片包；城巴 0 個車站、專線小巴 0、港鐵巴士 0 全部補返；同名站撈錯、港鐵巴士漏線、嶼巴英文名等剩低限制一併拆掉",
            titleZhHans = "离线地图瓦片包；城巴 0 个车站、专线小巴 0、港铁巴士 0 全部补返；同名站捞错、港铁巴士漏线、屿巴英文名等剩低限制一并拆掉",
            titleEn = "Offline map tile pack; Citybus 0 stops, GMB 0 and MTR Bus 0 filled in; remaining limitations: same-name merging, MTR Bus routes, NLB English names",
            itemsZhHant = listOf(
                "**離線地圖瓦片包：落咗之後，完全冇網絡都出到真實地圖**：上一版已經把 Leaflet 嘅 JS／CSS 打包入 APK，剩低瓦片仍然要等睇過嗰條路線先慢慢落 —— 即係第一次去一個新地方，地圖會空白一片。而家設定頁多咗「離線地圖瓦片包」：撳掣揀範圍（市區約 4 MB／全港約 9 MB／全港連離島約 11 MB）就一次過落晒，落完之後就算飛機模式都出到真實地圖，唔使再等網絡。三個細節：已落過嘅瓦片**唔會重複落**，所以斷線再撳係**續傳**；可以中途撳「停止」，已落嘅會留低；淨係落 zoom 11–15，再深會係幾萬枚，流量同空間都唔值",
                "**剩低嘅限制一併處理**：①**同名站最後一個窿都補埋** —— 之前「雙方都有座標」要相距 500 米內先併，但其中一方冇座標時仍然淨靠同名，第區同名站仍有機會撈錯；而家再加多一道**共享路線號**驗證：同一條路線唔會喺兩個唔同區各有一個同名站，所以有共同路線號基本可以肯定係同一個站牌，完全冇共同路線就唔併。②**港鐵巴士路線清單補齊** —— 官方冇全量路線接口，以前靠內置清單會漏線；而家清單涵蓋晒現行全線（屯門／元朗／天水圍／大埔接駁），將來官方加新線亦會由索引自動併入。③**嶼巴英文名由 109 個加到 154 個** —— 補咗東涌各屋苑、機場後勤區、愉景灣同大嶼山郊遊熱門站；仍然係**官方地名對照**而唔係機器翻譯，未命中一律維持中文，唔會亂砌一個似是而非嘅英文名",
                "**車站資料補充：索引統計入面嗰啲「0」全部補返**：以前統計長期顯示「城巴 407 條路線／**0** 個車站」「專線小巴 0／0」「港鐵巴士 0／0」—— 因為城巴官方**冇全量車站接口**、專線小巴同港鐵巴士嘅官方接口又成日失敗，淨靠逐條路線去補（幾百個請求）實測係「新增 0 個車站」。而家改用 **hkbus/hk-bus-crawling 每日聚合好嘅一份**（GitHub 開源項目，由 data.gov.hk、運輸署 GTFS、MTR open data 聚合）：**一個請求**就有齊全部營辦商嘅車站同名同座標，設定頁撳「下載／更新車站資料」一次過補晒，之後重建索引會自動併入。三個要點：①**只補缺少嘅，唔覆蓋已有嘅** —— 城巴路線繼續用官方嗰 407 條（最準），補嘅係車站同站序；九巴、嶼巴、港鐵、輕鐵**一律唔掂**，所以唔會出現「補完反而少咗」。②聚合檔入面 **`lrtfeeder` 就係港鐵巴士**（輕鐵係 `lightRail`），呢個對位錯咗港鐵巴士會永遠係 0。③檔案唔細，所以同官方班次資料一樣**淨係撳掣先落**，唔會開 App 自動下載；落咗會 cache 喺本機",
                "**專線小巴而家出到路線同名同站序**：官方 `/route/{HKI,KLN,NT}` 長期失敗，所以小巴以前連路線都係 0。而家由聚合檔補返全部小巴路線、車站同站序 —— 可以搜路線、睇沿途車站同地圖位置。要講明：小巴嘅站號係 GTFS 編號，同官方即時到站接口嘅編號唔同，所以**沿途車站出到，即時到站就未必查到**",
                "**儲存統計唔再顯示「0 條 · 0B」**：設定頁嗰行「儲存：Rust / store · N 個命名空間 · M 條 · X KB」以前會顯示「2 個命名空間 · **0** 條 · **0B**，明明磁碟上面有嘢 —— 因為 Rust store 係**懶載入**（第一次用到先讀碟），而統計淨係數**已載入**嗰啲。而家未載入嘅命名空間會淨數行數（順序讀一次，**唔會**為咗統計而喺記憶體砌一張表出嚟），所以顯示嘅係真實數字",
                "**車站英文名全面補充 —— 唔再淨係內置嗰 154 個**：嶼巴官方接口**冇**英文站名欄位（九巴、城巴、小巴都有），所以英文介面下嶼巴站名以前多數維持中文，內置對照表淨係 cover 到 154 個常用站。而家改用 **hkbus 聚合檔嘅 `stopList`** —— 入面每個站都有官方 `name.en`，**全部營辦商一齊補**（嶼巴、專線小巴、港鐵巴士、城巴、輕鐵、港鐵）。三層後備：官方 `name_en` → 內置對照表 → 聚合檔，三層都搵唔到先維持中文，**永遠唔會**砌一個似是而非嘅英文名。配對先對 stop id，對唔到就用**中文站名**對 —— 因為官方 id 同聚合檔 id 未必同源（例如小巴），但中文站名兩邊都係官方名，一定對得到。設定頁會顯示補咗幾多條對照",
                "**專線小巴即時到站唔再淨係靠 route_id**：聚合檔畀小巴嘅 route_id 係 GTFS 編號，同官方 `data.etagmb.gov.hk` 要用嘅 id 唔同源，帶住佢去查通常查唔到車。而家帶 route_id 查唔到嘅時候，會**唔帶 route_id 再撳一次** —— 官方接口本身支援淨用 stop_id 撳晒呢個站所有線，撳返嚟再本地按路線號篩，唔使知官方 id 係乜、亦唔使靠估。仍然要講明：**stop_id 唔同源嘅站依然會查唔到**，嗰啲會維持淨顯示沿途車站同名 —— 呢個係官方資料本身嘅限制",
                "**聚合資料加咗防倒退守門，壞資料唔可以覆蓋好資料**：車站資料係由 GitHub 上每日聚合嘅一份檔案補返嚟嘅，嗰份檔**唔係我哋控制** —— 聚合到一半、欄位改名、或者當日上游掛咗，都會出一份「數量正常但內容殘」嘅檔。以前照單全收，一次壞資料就會**覆蓋掉機上嗰份好嘅快取**，之後重建索引城巴車站由幾千變零，而且**冇任何提示**。而家落碟之前要過兩關：①**條目數暴跌就唔寫** —— 同上一次比跌超過 30% 就拒絕寫入，機上嗰份會原封不動留低；第一次落（冇得比）就改用絕對下限（少過 200 個站當壞資料）。資料變多或者小幅下跌一律照寫，唔會永久卡住喺一份舊資料。②**欄位命中率** —— 假如 `name.en` 改咗名做 `nameEn`，解析器唔會報錯、只係每條拎到空字串，英文名會全部消失並靜靜雞降級去內置嗰 154 個，用戶永遠唔會發現；所以睇咗五十個站以上而一個都拎唔到，就當格式變咗、**大聲失敗**而唔係靜默降級。兩關都唔過會喺設定頁見到具體原因，唔係一句「下載失敗」",
                "**第一次有單元測試，而且測唔過就唔出包**：之前所有「Rust 同 Kotlin 必定一致」嘅承諾（取樣、砌班次表、診斷、踢走最舊）淨係靠一次性嘅手動驗證 —— 之後改任何一邊，都冇人會提醒你兩邊唔夾咗。而家 CI 多咗一步「Run unit tests」，同 `compileReleaseKotlin` **平級**：測唔過就唔出 APK。第一批測試鎖住兩樣嘢：①`DataGuard` —— 上面嗰條嘅兩道關卡全部有測試（暴跌、第一次落、小幅下跌、英文名欄位消失、座標欄位消失）。②`RustKernel` 嘅 Kotlin 回退版 —— 取樣嘅 golden values（40 個站取 12 個會係 0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39）、診斷五種狀態、路線號正規化。要講明：呢啲測試鎖嘅係 **Kotlin 側** —— JVM 上面冇 `.so`，所以行嘅係回退版，即係測嘅正係「Rust 唔喺度嗰陣用戶會見到嘅結果」。要證明 Rust 側一樣，就要喺有 `.so` 嘅環境再跑同一組數字，所以**改 Rust 嗰邊之後要手動對返呢度每一個數字**",
                "**圖形後端改行 Vulkan，唔再依賴 OpenGL ES**：Android 17 起 OpenGL ES 已被官方標記為棄用，系統嘅 HWUI（Compose 介面同地圖 WebView 都行呢條管線）喺支援 Vulkan 嘅機上會自動改用 skiavk。Manifest 入面**刻意唔再聲明 glEsVersion** —— App 本身冇一行直接掂 OpenGL ES（冇 GLSurfaceView、冇 GLES20.*、NDK 亦冇連結 egl/gles），聲明咗只會令 Play 用一個 App 根本唔需要嘅硬性依賴去篩機；改為聲明 android.hardware.vulkan.level 級別 1，但 **required=false**，冇 Vulkan 嘅機照裝照用、會落返 skiagl。設定頁「索引統計」下面多咗一行「圖形：」，會講明部機嘅 Vulkan 版本、硬體級別同運算級別。兩點要講清楚：①**邊個後端由系統決定，App 改唔到** —— ro.hwui.use_vulkan 係唯讀屬性，debug.hwui.renderer 要 root 先改到，所以呢行係**報告而唔係開關**；②Android 10 以下系統唔會報告 Vulkan feature，嗰啲機會顯示「冇 Vulkan（系統冇報告）」，唔代表部機真係冇",
                        ),
            itemsZhHans = listOf(
                "**离线地图瓦片包：落咗之后，完全没有网络都出到真实地图**：上一版已经把 Leaflet 嘅 JS／CSS 打包入 APK，剩低瓦片仍然要等睇过嗰条路线先慢慢落 —— 即系第一次去一个新地方，地图会空白一片。而家设定页多咗「离线地图瓦片包」：㩒掣拣范围（市区约 4 MB／全港约 9 MB／全港连离岛约 11 MB）就一次过落晒，落完之后就算飞行模式都出到真实地图，唔使再等网络。三个细节：已落过嘅瓦片**唔会重复落**，所以断线再㩒系**续传**；可以中途㩒「停止」，已落嘅会留低；净系落 zoom 11–15，再深会系几万块，流量同空间都唔值",
                "**剩低嘅限制一并处理**：①**同名站最后一个窿都补埋** —— 之前「双方都有坐标」要相距 500 米内先并，但其中一方冇坐标时仍然净靠同名，第区同名站仍有机会捞错；而家再加多一道**共享路线号**验证：同一条路线唔会喺两个唔同区各有一个同名站，所以有共同路线号基本可以肯定系同一个站牌，完全冇共同路线就唔并。②**港铁巴士路线清单补齐** —— 官方冇全量路线接口，以前靠内置清单会漏线；而家清单涵盖晒现行全线（屯门／元朗／天水围／大埔接驳），将来官方加新线亦会由索引自动并入。③**屿巴英文名由 109 个加到 154 个** —— 补咗东涌各屋苑、机场后勤区、愉景湾同大屿山郊游热门站；仍然系**官方地名对照**而唔系机器翻译，未命中一律维持中文，唔会乱砌一个似是而非嘅英文名",
                "**车站资料补充：索引统计入面嗰啲「0」全部补返**：以前统计长期显示「城巴 407 条路线／**0** 个车站」「专线小巴 0／0」「港铁巴士 0／0」—— 因为城巴官方**冇全量车站接口**、专线小巴同港铁巴士嘅官方接口又成日失败，净靠逐条路线去补（几百个请求）实测系「新增 0 个车站」。而家改用 **hkbus/hk-bus-crawling 每日聚合好嘅一份**（GitHub 开源项目，由 data.gov.hk、运输署 GTFS、MTR open data 聚合）：**一个请求**就有齐全部营办商嘅车站同名同坐标，设定页㩒「下载／更新车站资料」一次过补晒，之后重建索引会自动并入。三个要点：①**只补缺少嘅，唔覆盖已有嘅** —— 城巴路线继续用官方嗰 407 条（最准），补嘅系车站同站序；九巴、屿巴、港铁、轻铁**一律唔掂**，所以唔会出现「补完反而少咗」。②聚合档入面 **`lrtfeeder` 就系港铁巴士**（轻铁系 `lightRail`），呢个对位错咗港铁巴士会永远系 0。③档案唔细，所以同官方班次资料一样**净系㩒掣先落**，唔会开 App 自动下载；落咗会 cache 喺本机",
                "**专线小巴而家出到路线同名同站序**：官方 `/route/{HKI,KLN,NT}` 长期失败，所以小巴以前连路线都系 0。而家由聚合档补返全部小巴路线、车站同站序 —— 可以搜路线、睇沿途车站同地图位置。要讲明：小巴嘅站号系 GTFS 编号，同官方即时到站接口嘅编号唔同，所以**沿途车站出到，即时到站就未必查到**",
                "**储存统计唔再显示「0 条 · 0B」**：设定页嗰行「储存：Rust / store · N 个命名空间 · M 条 · X KB」以前会显示「2 个命名空间 · **0** 条 · **0B**，明明磁盘上面有嘢 —— 因为 Rust store 系**懒载入**（第一次用到先读碟），而统计净系数**已载入**嗰啲。而家未载入嘅命名空间会净数行数（顺序读一次，**唔会**为咗统计而喺内存砌一张表出嚟），所以显示嘅系真实数字",
                "**车站英文名全面补充 —— 唔再净系内置嗰 154 个**：屿巴官方接口**冇**英文站名字段（九巴、城巴、小巴都有），所以英文界面下屿巴站名以前多数维持中文，内置对照表净系 cover 到 154 个常用站。而家改用 **hkbus 聚合档嘅 `stopList`** —— 入面每个站都有官方 `name.en`，**全部营办商一齐补**（屿巴、专线小巴、港铁巴士、城巴、轻铁、港铁）。三层后备：官方 `name_en` → 内置对照表 → 聚合档，三层都搵唔到先维持中文，**永远唔会**砌一个似是而非嘅英文名。配对先对 stop id，对唔到就用**中文站名**对 —— 因为官方 id 同聚合档 id 未必同源（例如小巴），但中文站名两边都系官方名，一定对得到。设定页会显示补咗几多条对照",
                "**专线小巴即时到站唔再净系靠 route_id**：聚合档畀小巴嘅 route_id 系 GTFS 编号，同官方 `data.etagmb.gov.hk` 要用嘅 id 唔同源，带住佢去查通常查唔到车。而家带 route_id 查唔到嘅时候，会**唔带 route_id 再㩒一次** —— 官方接口本身支持净用 stop_id 㩒晒呢个站所有线，㩒返嚟再本地按路线号筛，唔使知官方 id 系乜、亦唔使靠估。仍然要讲明：**stop_id 唔同源嘅站依然会查唔到**，嗰啲会维持净显示沿途车站同名 —— 呢个系官方数据本身嘅限制",
                "**聚合资料加咗防倒退守门，坏资料唔可以覆盖好资料**：车站资料系由 GitHub 上每日聚合嘅一份档案补返嚟嘅，嗰份档**唔系我哋控制** —— 聚合到一半、栏位改名、或者当日上游挂咗，都会出一份「数量正常但内容残」嘅档。以前照单全收，一次坏资料就会**覆盖掉机上嗰份好嘅快取**，之后重建索引城巴车站由几千变零，而且**冇任何提示**。而家落碟之前要过两关：①**条目数暴跌就唔写** —— 同上一次比跌超过 30% 就拒绝写入，机上嗰份会原封不动留低；第一次落（冇得比）就改用绝对下限（少过 200 个站当坏资料）。资料变多或者小幅下跌一律照写，唔会永久卡住喺一份旧资料。②**栏位命中率** —— 假如 `name.en` 改咗名做 `nameEn`，解析器唔会报错、只系每条拎到空字串，英文名会全部消失并静鸡鸡降级去内置嗰 154 个，用户永远唔会发现；所以睇咗五十个站以上而一个都拎唔到，就当格式变咗、**大声失败**而唔系静默降级。两关都唔过会喺设定页见到具体原因，唔系一句「下载失败」",
                "**第一次有单元测试，而且测唔过就唔出包**：之前所有「Rust 同 Kotlin 必定一致」嘅承诺（取样、砌班次表、诊断、踢走最旧）净系靠一次性嘅手动验证 —— 之后改任何一边，都冇人会提醒你两边唔夹咗。而家 CI 多咗一步「Run unit tests」，同 `compileReleaseKotlin` **平级**：测唔过就唔出 APK。第一批测试锁住两样嘢：①`DataGuard` —— 上面嗰条嘅两道关卡全部有测试（暴跌、第一次落、小幅下跌、英文名栏位消失、坐标栏位消失）。②`RustKernel` 嘅 Kotlin 回退版 —— 取样嘅 golden values（40 个站取 12 个会系 0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39）、诊断五种状态、路线号正规化。要讲明：呢啲测试锁嘅系 **Kotlin 侧** —— JVM 上面冇 `.so`，所以行嘅系回退版，即系测嘅正系「Rust 唔喺度嗰阵用户会见到嘅结果」。要证明 Rust 侧一样，就要喺有 `.so` 嘅环境再跑同一组数字，所以**改 Rust 嗰边之后要手动对返呢度每一个数字**",
                "**图形后端改行 Vulkan，唔再依赖 OpenGL ES**：Android 17 起 OpenGL ES 已被官方标记为弃用，系统的 HWUI（Compose 界面同地图 WebView 都行呢条管线）喺支持 Vulkan 的机上会自动改用 skiavk。Manifest 入面**刻意唔再声明 glEsVersion** —— App 本身冇一行直接掂 OpenGL ES（冇 GLSurfaceView、冇 GLES20.*、NDK 亦冇链接 egl/gles），声明咗只会令 Play 用一个 App 根本唔需要的硬性依赖去筛机；改为声明 android.hardware.vulkan.level 级别 1，但 **required=false**，冇 Vulkan 的机照装照用、会落返 skiagl。设定页「索引统计」下面多咗一行「图形：」，会讲明部机的 Vulkan 版本、硬件级别同运算级别。两点要讲清楚：①**边个后端由系统决定，App 改唔到** —— ro.hwui.use_vulkan 系只读属性，debug.hwui.renderer 要 root 先改到，所以呢行系**报告而唔系开关**；②Android 10 以下系统唔会报告 Vulkan feature，嗰啲机会显示「冇 Vulkan（系统冇报告）」，唔代表部机真系冇",
                        ),
            itemsEn = listOf(
                "**Offline map tile pack: the real map renders with no network at all once it is downloaded**: the previous version already bundled the Leaflet JS and CSS inside the APK, but the tiles themselves still had to be pulled lazily the first time you viewed a route, so opening a new area left the map blank. Settings now carries an **Offline map tile pack**: tap and pick a coverage (urban about 4 MB, whole territory about 9 MB, whole territory including the outlying islands about 11 MB) and every tile in that box is fetched in one go; afterwards the real map renders even in aeroplane mode. Three details worth knowing: tiles already on the phone are **never re-downloaded**, so tapping again after a disconnect simply **resumes**; you can hit **Stop** part-way and everything fetched so far is kept; and only zoom 11 to 15 is fetched, since each further level quadruples the count and is not worth the data or the space",
                "**The remaining limitations are dealt with too**: (1) **The last same-name gap is closed** - when both stops had coordinates they already had to lie within 500 m to be merged, but if one side had no coordinates the name match was used on its own and a same-named stop in another district could still be merged by mistake. A **shared route number** test is now added on top: one route cannot have two same-named stops in different districts, so a shared route number means it is almost certainly the same sign, and stops with no route in common are left alone. (2) **The MTR Bus route list is now complete** - there is no full route API, so the built-in list used to miss routes; it now covers every current route (Tuen Mun, Yuen Long, Tin Shui Wai and the Tai Po feeders), and routes the index discovers later are folded in automatically. (3) **NLB English stop names grow from 109 to 154** - the Tung Chung estates, the airport support area, Discovery Bay and popular Lantau country-park stops are now covered. These remain **official place-name matches rather than machine translation**: anything unmatched stays Chinese instead of being given a plausible-looking invented English name",
                "**Stop data supplement: every 0 in the index stats is now filled in**: the stats used to read Citybus 407 routes / **0** stops, GMB 0 / 0 and MTR Bus 0 / 0, because Citybus has **no all-stops API** and the GMB / MTR Bus endpoints fail often; filling them route by route (hundreds of requests) reliably ended in **0 stops added**. This version switches to the **daily file aggregated by hkbus/hk-bus-crawling** (open source on GitHub, merging data.gov.hk, the TD GTFS feed and MTR open data): **one request** carries the stops and coordinates of every operator. Tap Download / update stop data in Settings and it is filled in at once; rebuilding the index afterwards merges it automatically. Three notes: ① it **only fills gaps, never overwrites** — Citybus keeps its official 407 routes, and what is added are the stops and their order; KMB, NLB, MTR and Light Rail are **left untouched**, so nothing can get worse. ② in that file **`lrtfeeder` is MTR Bus** (Light Rail is `lightRail`) — mix those up and MTR Bus stays at 0 forever. ③ the file is not small, so like the frequency data it is **only fetched when you tap**, never at startup; once fetched it is cached locally",
                "**GMB now has routes and stop sequences**: the official `/route/{HKI,KLN,NT}` endpoint has been failing for a long time, so minibuses used to show even 0 routes. The aggregated file now supplies every GMB route, stop and sequence — you can search a route, see its stops and find them on the map. One caveat: GMB stop ids there are GTFS ids, which differ from the ids used by the official arrival API, so **stops and sequences are shown while live arrivals may not resolve**",
                "**Storage stats no longer show 0 entries · 0 B**: the Settings line used to read **2 namespaces · 0 entries · 0 B** even though the data was on disk, because the Rust store is **lazily loaded** (a namespace is read on first use) and the counter only counted **loaded** ones. Unloaded namespaces are now counted by scanning line count once, **without** building a map in memory just to produce a statistic, so the line shows the real numbers",
                "**English stop names for every operator, not just the built-in 154**: the NLB API has **no** English stop-name field (KMB, Citybus and GMB all do), so under an English interface most NLB stops used to stay Chinese and the built-in table covered only 154 common stops. This version uses **`stopList` from the hkbus aggregated file** instead - every stop there carries an official `name.en`, so **all operators are filled in at once** (NLB, GMB, MTR Bus, Citybus, Light Rail and MTR). It is a three-level fallback: official `name_en`, then the built-in table, then the aggregated file, and only if all three miss does the name stay Chinese - a plausible-looking invented English name is **never** produced. Matching tries the stop id first and the **Chinese name** second, because official ids and aggregated ids do not always share a source (GMB, for example) while the Chinese name is official on both sides and always matches. Settings shows how many pairs were loaded",
                "**GMB live arrivals no longer depend on the route id alone**: the route id the aggregated file gives for minibuses is a GTFS id, which does not share a source with the id the official `data.etagmb.gov.hk` endpoint expects, so querying with it usually returned nothing. Now, when the query with a route id comes back empty, the app **retries without the route id** - the official endpoint accepts a stop id on its own and returns every route serving that stop, and the result is then filtered locally by route number. No need to know the official id, no guessing. One caveat remains: **stops whose stop id does not share a source still return nothing**, and those keep showing just the route with its stop list - that is a limit of the official data itself",
                "**Guard against bad upstream data — a bad download can no longer overwrite your good one**: the stop data comes from a daily aggregated file on GitHub, which **we do not control**. If their aggregation half-fails, if a field is renamed, or if an upstream source is down that day, you get a file that looks normal in size but is broken inside. Previously that was accepted as-is, so one bad download would **overwrite the good cache already on your phone** — rebuild the index afterwards and Citybus stops would drop from thousands to zero, with **no warning at all**. Now every download has to pass two gates before it is written: ①**reject sudden drops** — if the entry count falls by more than 30% compared with last time, the write is refused and the existing copy is left untouched (on a first download there is nothing to compare against, so an absolute floor is used instead: fewer than 200 stops means the data is broken). Growth and small declines are always written, so you never get stuck on stale data. ②**field hit-rate** — if `name.en` were renamed to `nameEn`, the parser would not error, it would just read an empty string every time: English names would silently disappear and fall back to the built-in 154, and nobody would ever notice. So if more than fifty stops are seen and not a single one yields a value, that is treated as a format change and reported **loudly** instead of degrading silently. If either gate fails you see the actual reason in Settings, not just \"download failed\"",
                "**Unit tests for the first time — and a failing test blocks the release**: every promise that \"Rust and Kotlin always agree\" (sampling, timetable building, diagnosis, eviction) was previously backed only by a one-off manual check. Change either side afterwards and nothing would tell you the two had drifted apart. CI now has a \"Run unit tests\" step at the **same level** as `compileReleaseKotlin`: if a test fails, no APK is produced. The first batch locks down two things: ①`DataGuard` — every gate above has tests (sudden drop, first download, small decline, English-name field gone, coordinate field gone). ②the Kotlin fallback in `RustKernel` — golden values for sampling (40 stops sampled down to 12 gives 0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39), all five diagnosis states, and route-number normalisation. One caveat worth stating: these tests lock the **Kotlin side**. There is no `.so` on a JVM, so what runs is the fallback — which means they test exactly what users see when Rust is not present. Proving the Rust side matches means running the same numbers in an environment that has the `.so`, so **after changing anything on the Rust side, check every number here by hand**",
                "**Graphics backend moves to Vulkan; no longer depends on OpenGL ES**: Android 17 marks OpenGL ES as deprecated, and HWUI (the pipeline both the Compose UI and the map WebView run on) switches to skiavk automatically on devices that support Vulkan. The manifest now **deliberately does not declare glEsVersion** — the app has no line of code touching OpenGL ES (no GLSurfaceView, no GLES20.*, and the NDK links neither egl nor gles), so declaring it would only make Play filter devices on a hard dependency the app never needed. Instead it declares android.hardware.vulkan.level 1 with **required=false**, so devices without Vulkan still install and simply fall back to skiagl. A new Graphics: line under the index stats reports the device Vulkan version, hardware level and compute level. Two things worth stating plainly: ①**which backend is used is decided by the system, an app cannot change it** — ro.hwui.use_vulkan is read-only and debug.hwui.renderer needs root, so this line is a **report, not a switch**; ②below Android 10 the system does not report Vulkan features at all, so those devices show no Vulkan (not reported), which does not mean the device actually lacks it",
            ),
        ),
        Entry(
            version = "1.2.1",
            date = "2026-10-14",
            titleZhHant = "附近車站剝走括號、淨出最近一班、同名併入城巴嶼巴港鐵巴士，並解決同名撈錯、淨開單一營辦商、班次顯示唔晒等限制",
            titleZhHans = "附近车站剥走括号、净出最近一班、同名并入城巴屿巴港铁巴士，并解决同名捞错、净开单一营办商、班次显示唔晒等限制",
            titleEn = "Nearby: strip brackets, next departure only, merge Citybus / NLB / MTR Bus by name, and fix the remaining Nearby limitations",
            itemsZhHant = listOf(
                "**「附近」車站卡連站號括號都唔顯示**：站名後面嗰舊括號站號（例如「海麗邨巴士總站 (SS667)」嗰個 SS667）而家會剝走 —— 附近個卡位得咁上下闊，多咗舊號碼只會阻地方；而且同一個站唔同營辦商有唔同站號（九巴一個、城巴另一個），顯示邊一個都唔啱。淨係剝「括號入面正好係站號」嗰種，其他括號（例如「（循環線）」）會照保留，唔會改錯官方站名。車站頁同 ETA 卡仍然顯示完整站名連站號，想對返官方站牌嗰陣仲有得睇",
                "**「附近」淨顯示每條路線每個方向嘅最近一班，循環線、特別班次淨顯示一班**：以前一張附近卡淨係得路線號，想知幾時有車要撳入去。而家直接喺卡上面出「路線號　往邊度　幾分鐘」—— 一條雙向路線喺同一個站會撈到兩堆班次（去程、回程），**每個方向淨係出最近嗰一班**，唔會倒十幾行出嚟；而**循環線、特別班次**呢類單向路線全部班次都係同一個方向，再分就冇意義，所以**淨係出最近嗰一班**。判斷係咪單向唔靠硬砌清單（九巴 1 號同城巴 1 號係兩條唔同嘅線，靠名單一定會錯），而係用索引入面呢個站嘅 bound 數量。班次係後台抓嘅，所以列表會即刻出、班次之後補上；抓唔到就淨係出路線號，同以前一樣",
                "**「附近」會併入城巴、嶼巴、港鐵巴士喺同一個站名嘅路線**：城巴、嶼巴、港鐵巴士有唔少站**座標缺失或者偏咗一截**，以前同名合併嘅寬容度得 500 米，呢啲站全部被篩走晒 —— 所以「附近」永遠淨係見到九巴。而家同名合併放寬到 **3 公里**，同名就併入同一張卡，城巴、嶼巴、港鐵巴士嘅路線會同九巴路線一齊顯示喺同一張卡上面。仲有一處要改：每條路線會記住**佢屬於邊個站**（九巴同城巴喺同一個站牌有唔同 station id），否則用九巴嘅 id 去查城巴路線一定查唔到車。為咗唔好無端端多一倍請求，設咗上限：**最多 8 張卡、每張最多 6 條非九巴路線**；九巴接口一次過返晒成個站，唔使逐條查",
                "**搜尋路線嗰頁改成乾淨版面，輸入框下面直接就係結果**：以前輸入框下面仲有「鐵路」「附近」兩個大掣，再加一行索引狀態（例如「城巴深度索引完成：新增 0 個車站」）同進度條，搞到結果列表要撳落去先睇到。而家「鐵路」「附近」搬咗去頂部標題列右邊，索引狀態同進度條**淨係載入中先顯示**，平時唔會霸位；結果列表亦唔再印「路線」「車站」呢啲分組大字（有分頁已經講明係乜）。一條路線一行：左邊路線號、右邊箭頭，非九巴路線第二行會標註營辦商官方全名（例如「大嶼山巴士」）同「資料由 DATA.GOV.HK 提供」",
                "**解決「定位搵附近車站」嘅三個限制**：①**同名站唔會再撈錯** —— 以前淨係 check「對方喺定位中心 3 公里內」就當同一個站，隔籬區嘅同名站都會被併埋落同一張卡；而家**雙方都有座標**時要相距 500 米內（真係同一個站牌）先併，只有其中一方冇座標（例如城巴未補到經緯度）先會淨靠同名併入，所以第區同名站唔會再撈錯，而缺座標嘅城巴、嶼巴站仍然出現到。②**撳卡片會開成個合併組，唔再淨係開一個營辦商嘅站** —— 以前撳落去淨係開路線最多嗰個站，城巴嗰份因為 station id 唔同會查唔到車；而家主站照行原本嘅快取／離線流程，合併組其他站後台撈埋再併入同一個列表，每筆都標埋邊間公司。③**班次唔再限制 8 張卡、每張 6 條路線** —— 以前城巴、嶼巴路線多過 6 條就顯示唔晒；而家全部都抓，改為用併發上限（同時最多 6 個請求）節流，唔會一次打幾十個請求打爆官方接口",
                "**支援官方班次間隔（第三方資料）**：香港各營辦商嘅即時到站接口全部淨係提供 ETA，冇發車時刻表、冇班次間隔、冇頭尾班車 —— 所以以前班次間隔要靠相鄰兩班 ETA 去估、營運時段要靠觀察累積。但**運輸署其實有「公共交通服務班次資料」**（GTFS，每兩星期更新），入面就係官方班次間隔；只係嗰個係 30 份 GTFS 嘅大 zip，手機下載同解析成本太高。所以呢版改為用 **hkbus/hk-bus-crawling 每日聚合好嘅版本**（data.hkbus.app），佢本身由 data.gov.hk、運輸署 GTFS 同 1823.gov.hk 公眾假期聚合，每個 route 有官方班次間隔同假期清單。而家路線頁會顯示**官方班次間隔**（例如「12–18 分鐘」）同**表列營運時段**。要著佢：去設定頁撳「下載／更新」掣（因為檔案唔細，唔會開 App 自動幫你落）。**冇下載都唔影響任何功能** —— 班次間隔照舊由 ETA 推算，營運時段照舊觀察累積。兩點要講明：表列營運時段係由班次表時段**推算**出嚟，**唔係**官方頭／尾班車（官方資料淨係畀時段起點，冇結束時間）；今日係咪公眾假期會提示，但**唔會**自動切換假日班次（因為邊個 serviceType 係假日班次未驗證到）",
                "**改用運輸署 GTFS 原檔，補返官方時段結束時間同例假日表**：上一版嘅官方班次間隔用緊第三方聚合檔，方便但先天缺兩樣 —— ① 佢淨係畀時段**起點**、冇結束時間，所以「營運時段」係推算，過咗只可以講「已過表列時段（唔代表冇車）」；② 假日要用邊個 serviceType 冇得驗證，所以淨係提示、唔自動揀。運輸署原檔嘅 `frequencies.txt` **有 `end_time`**，`calendar.txt` 有**每條 service 邊日行**，`calendar_dates.txt` 有**個別日子加開／停開** —— 正好補嗰兩個窿。所以而家路線頁講得出「表列服務到幾點」，今日官方冇班次亦會直接講「今日官方表列冇班次」。為咗唔使下載成個 30 份 GTFS 嘅大 zip，呢版**淨係落 6 個細文字檔**（`stop_times.txt` 完全唔落，班次間隔唔需要逐站時間），而且 CSV **按表頭名**搵欄位、唔靠欄位順序，欄位多咗都唔怕。一樣係 opt-in（設定頁撳掣先落，檔案唔細唔會自動幫你落），**冇下載／下載失敗都唔影響任何功能** —— 會自動退回上一版嗰份聚合資料",
            ),
            itemsZhHans = listOf(
                "**「附近」车站卡连站号括号都唔显示**：站名后面嗰旧括号站号（例如「海丽邨巴士总站 (SS667)」嗰个 SS667）而家会剥走 —— 附近个卡位得咁上下阔，多咗旧号码只会阻地方；而且同一个站唔同营办商有唔同站号（九巴一个、城巴另一个），显示边一个都唔啱。净系剥「括号入面正好系站号」嗰种，其他括号（例如「（循环线）」）会照保留，唔会改错官方站名。车站页同 ETA 卡仍然显示完整站名连站号，想对返官方站牌嗰阵仲有得睇",
                "**「附近」净显示每条路线每个方向嘅最近一班，循环线、特别班次净显示一班**：以前一张附近卡净系得路线号，想知几时有车要撳入去。而家直接喺卡上面出「路线号　往边度　几分钟」—— 一条双向路线喺同一个站会捞到两堆班次（去程、回程），**每个方向净系出最近嗰一班**，唔会倒十几行出嚟；而**循环线、特别班次**呢类单向路线全部班次都系同一个方向，再分就冇意义，所以**净系出最近嗰一班**。判断系咪单向唔靠硬砌清单（九巴 1 号同城巴 1 号系两条唔同嘅线，靠名单一定会错），而系用索引入面呢个站嘅 bound 数量。班次系后台抓嘅，所以列表会即刻出、班次之后补上；抓唔到就净系出路线号，同以前一样",
                "**「附近」会并入城巴、屿巴、港铁巴士喺同一个站名嘅路线**：城巴、屿巴、港铁巴士有唔少站**坐标缺失或者偏咗一截**，以前同名合并嘅宽容度得 500 米，呢啲站全部被筛走晒 —— 所以「附近」永远净系见到九巴。而家同名合并放宽到 **3 公里**，同名就并入同一张卡，城巴、屿巴、港铁巴士嘅路线会同九巴路线一齐显示喺同一张卡上面。仲有一处要改：每条路线会记住**佢属于边个站**（九巴同城巴喺同一个站牌有唔同 station id），否则用九巴嘅 id 去查城巴路线一定查唔到车。为咗唔好无端端多一倍请求，设咗上限：**最多 8 张卡、每张最多 6 条非九巴路线**；九巴接口一次过返晒成个站，唔使逐条查",
                "**搜寻路线嗰页改成干净版面，输入框下面直接就系结果**：以前输入框下面仲有「铁路」「附近」两个大掣，再加一行索引状态（例如「城巴深度索引完成：新增 0 个车站」）同进度条，搞到结果列表要撳落去先睇到。而家「铁路」「附近」搬咗去顶部标题列右边，索引状态同进度条**净系载入中先显示**，平时唔会霸位；结果列表亦唔再印「路线」「车站」呢啲分组大字（有分页已经讲明系乜）。一条路线一行：左边路线号、右边箭头，非九巴路线第二行会标注营办商官方全名（例如「大屿山巴士」）同「资料由 DATA.GOV.HK 提供」",
                "**解决「定位搵附近车站」嘅三个限制**：①**同名站唔会再捞错** —— 以前净系 check「对方喺定位中心 3 公里内」就当同一个站，隔离区嘅同名站都会被并埋落同一张卡；而家**双方都有坐标**时要相距 500 米内（真系同一个站牌）先并，只有其中一方冇坐标（例如城巴未补到经纬度）先会净靠同名并入，所以第区同名站唔会再捞错，而缺坐标嘅城巴、屿巴站仍然出现到。②**撳卡片会开成个合并组，唔再净系开一个营办商嘅站** —— 以前撳落去净系开路线最多嗰个站，城巴嗰份因为 station id 唔同会查唔到车；而家主站照行原本嘅缓存／离线流程，合并组其他站后台捞埋再并入同一个列表，每笔都标埋边间公司。③**班次唔再限制 8 张卡、每张 6 条路线** —— 以前城巴、屿巴路线多过 6 条就显示唔晒；而家全部都抓，改为用并发上限（同时最多 6 个请求）节流，唔会一次打几十个请求打爆官方接口",
                "**支持官方班次间隔（第三方数据）**：香港各营办商嘅即时到站接口全部净系提供 ETA，冇发车时刻表、冇班次间隔、冇头尾班车 —— 所以以前班次间隔要靠相邻两班 ETA 去估、营运时段要靠观察累积。但**运输署其实有「公共交通服务班次资料」**（GTFS，每两星期更新），入面就系官方班次间隔；只系嗰个系 30 份 GTFS 嘅大 zip，手机下载同解析成本太高。所以呢版改为用 **hkbus/hk-bus-crawling 每日聚合好嘅版本**（data.hkbus.app），佢本身由 data.gov.hk、运输署 GTFS 同 1823.gov.hk 公众假期聚合，每个 route 有官方班次间隔同假期清单。而家路线页会显示**官方班次间隔**（例如「12–18 分钟」）同**表列营运时段**。要著佢：去设定页㩒「下载／更新」掣（因为档案唔细，唔会开 App 自动帮你落）。**冇下载都唔影响任何功能** —— 班次间隔照旧由 ETA 推算，营运时段照旧观察累积。两点要讲明：表列营运时段系由班次表时段**推算**出嚟，**唔系**官方头／尾班车（官方资料净系畀时段起点，冇结束时间）；今日系咪公众假期会提示，但**唔会**自动切换假日班次（因为边个 serviceType 系假日班次未验证到）",
                "**改用运输署 GTFS 原档，补返官方时段结束时间同例假日表**：上一版嘅官方班次间隔用紧第三方聚合档，方便但先天缺两样 —— ① 佢净系畀时段**起点**、冇结束时间，所以「营运时段」系推算，过咗只可以讲「已过表列时段（唔代表冇车）」；② 假日要用边个 serviceType 冇得验证，所以净系提示、唔自动拣。运输署原档嘅 `frequencies.txt` **有 `end_time`**，`calendar.txt` 有**每条 service 边日行**，`calendar_dates.txt` 有**个别日子加开／停开** —— 正好补嗰两个窿。所以而家路线页讲得出「表列服务到几点」，今日官方冇班次亦会直接讲「今日官方表列冇班次」。为咗唔使下载成个 30 份 GTFS 嘅大 zip，呢版**净系落 6 个细文字档**（`stop_times.txt` 完全唔落，班次间隔唔需要逐站时间），而且 CSV **按表头名**搵栏位、唔靠栏位顺序，栏位多咗都唔怕。一样系 opt-in（设定页㩒掣先落，档案唔细唔会自动帮你落），**冇下载／下载失败都唔影响任何功能** —— 会自动退回上一版嗰份聚合数据",
            ),
            itemsEn = listOf(
                "**Nearby stop cards also drop the bracketed stop code**: the code in brackets after the stop name (the SS667 in a name such as Hoi Lai Estate Bus Terminus (SS667)) is now stripped. The nearby card is only so wide, and the extra number just eats space; worse, the same physical stop has a different code per operator (one for KMB, another for Citybus), so showing either one would be wrong. Only a bracket whose contents are exactly the stop code is removed, so other brackets (a circular-service note, for example) are kept and official stop names are never mangled. The stop page and ETA cards still show the full name with the code, so you can still match it against the sign at the stop",
                "**Nearby cards now show the next departure per route and direction**: a nearby card used to list route numbers only, so you had to tap through to see when anything arrives. Each card now shows *route · towards · minutes*. A two-way route returns two piles of departures at the same stop (outbound and inbound), so **only the nearest one is shown per direction** instead of dumping a dozen rows; **circular routes and special trips** are one-way - every departure runs the same way, so splitting by direction is meaningless and **only the single nearest departure is shown**. Whether a route is one-way is decided from the number of `bound` values at that stop in the index, not from a hard-coded list (KMB 1 and CTB 1 are completely different routes, so a fixed list would be wrong). Departures are fetched in the background, so the list appears immediately and the times fill in afterwards; if the fetch fails the card falls back to plain route numbers exactly as before",
                "**Nearby now folds in Citybus, NLB and MTR Bus routes at stops that share a name**: many Citybus, NLB and MTR Bus stops have missing or badly offset coordinates, and the name-match tolerance used to be only 500 m, so all of them were filtered out — Nearby looked KMB-only no matter what. Name matching is now widened to **3 km**: a stop with the same name folds into one card, so Citybus, NLB and MTR Bus routes appear alongside the KMB ones. A second fix was needed too: each route now remembers **which stop it belongs to** (KMB and Citybus use different station ids for the same sign), because querying a Citybus route with a KMB stop id always returned nothing. To stop the request count from doubling, the merge is capped: **at most 8 cards, and at most 6 non-KMB routes each**; the KMB endpoint returns a whole stop in one call, so its routes never cost an extra request",
                "**The route search screen is now a clean list: results start right under the input box**: the input box used to be followed by two large Rail and Nearby buttons plus a line of index status (something like Citybus deep index finished: 0 stops added) and a progress bar, which pushed the actual results below the fold. Rail and Nearby have moved to the right-hand side of the top bar; the index status and progress bar **appear only while something is loading** instead of occupying a row permanently; and the results no longer print group headings (the tab already tells you which kind of result this is). One route per row: route number on the left, chevron on the right, and non-KMB routes carry a second line with the operator official name (New Lantao Bus, for example) and the data.gov.hk attribution",
                "**Three Nearby limitations are fixed**: ①**Same-named stops are no longer merged by mistake** — the old test only checked that the other stop lay within 3 km of the located centre, so a same-named stop in a neighbouring district could be folded onto the same card. Now, when **both stops have coordinates** they must lie within 500 m of each other (a genuine shared bus stop) before merging; only when one side has no coordinates (Citybus stops that never received lat/lon, for example) is the name match used on its own. Same-named stops in another district no longer get merged, while coordinate-less Citybus and NLB stops still surface. ②**Tapping a card opens the whole merged group, not just one operator stop** — tapping used to open whichever operator had the most routes, and the Citybus half stayed invisible because a different station id meant its departures could never be queried. Now the primary stop keeps its normal cache and offline path while the other stops in the group are fetched in the background and merged into one list, each entry labelled with its operator. ③**Departure fetching is no longer capped at 8 cards and 6 routes each** — Citybus and NLB routes beyond the sixth used to be invisible. Everything is fetched now, throttled by a concurrency limit (at most 6 requests in flight) so a single stop cannot fire dozens of requests at the official API",
                "**Official frequency is now supported (third-party data)**: every Hong Kong operator arrival API returns only ETA, with no published timetable, no frequency and no first or last bus. Frequency therefore used to be estimated from two consecutive ETAs, and the service window accumulated from observation. **The Transport Department does publish frequency data** (GTFS, updated fortnightly), but it ships as one large zip holding 30 GTFS feeds, far too heavy for a phone to download and parse. This version instead uses the **daily build aggregated by hkbus/hk-bus-crawling** (data.hkbus.app), which merges data.gov.hk, the TD GTFS feed and 1823.gov.hk public holidays, and carries official frequency plus the holiday list for each route. The route screen now shows the **official frequency** (for example 12–18 min) and the **listed service window**. To switch it on, tap Download / update in Settings, since the file is not small and is never fetched automatically at startup. **Nothing breaks without it**: frequency still falls back to the ETA estimate and the window to observed accumulation. Two caveats: the listed window is **derived** from the frequency slots and is **not** the official first or last bus, because the source gives slot start times only with no end time; public holiday status is shown as a hint, but holiday frequency is **not** selected automatically, since which serviceType carries holiday service is unverified",
                "**Switched to the Transport Department GTFS raw feed, which adds official end times and the exception calendar**: the aggregated third-party file used previously was convenient but missing two things - it gave only slot **start** times and no end time, so the service window had to be derived and, once past it, the app could only say *past the listed window (buses may still run)*; and which `serviceType` carries holiday service could not be verified, so holidays were only a hint. The TD raw feed has **`end_time`** in `frequencies.txt`, **which days each service runs** in `calendar.txt`, and **per-date added/removed service** in `calendar_dates.txt` - exactly those two gaps. The route page can now state *listed service until HH:mm*, and when today has no listed service it says so outright. To avoid shipping the one large zip holding 30 GTFS feeds, this version downloads **only 6 small text files** (`stop_times.txt` is skipped entirely - headways do not need per-stop times), and reads CSV columns **by header name rather than position**, so extra or reordered columns are harmless. Still opt-in (tap in Settings; the files are not small and are never fetched automatically) and **nothing breaks without it** - it falls back to the aggregated data from the previous item",
            ),
        ),
        Entry(
            version = "1.2.0",
            date = "2026-10-14",
            titleZhHant = "附近車站睇到城巴；更新提示改細",
            titleZhHans = "附近车站睇到城巴；更新提示改细",
            titleEn = "Nearby stops now show Citybus; lighter update prompt",
            itemsZhHant = listOf(
                "**撳版本膠囊唔再彈「發現新版本」大對話框**：以前撳底部狀態欄嗰粒版本膠囊（或者入面嘅 ⟳）會即刻彈一個大對話框，入面塞住成段 Release notes 同對照表，喺電話上面要捲好耐。**而家改為靜默檢查**：有新版本會喺底部彈一條細提示條，寫住發現新版本 vX.Y.Z 附一個「下載更新」掣，撳咗就直接下載＋校驗＋開安裝器；已經係最新版、或者檢查失敗，都只係彈一句短訊。膠囊上面三個掣（⟳ 檢查更新、◷ 版本歷史、▤ 複製版本號）**全部保留**，有新版本嗰陣版本號數字照舊轉紅。想睇完整 Release notes 就去設定頁撳「檢查更新」（嗰度**保留**大對話框）或者撳 ◷ 開版本歷史",
                "**「附近」嘅車站卡淨係顯示站名同路線，而且會砌埋同一個站嘅城巴路線**：以前每張卡有一行「九巴 99 米」，即係一個站淨係顯示單一營辦商嘅路線 —— 九巴同城巴喺同一個巴士總站各有自己嘅 station id（例如九巴係 SS667，城巴係另一個），所以畫面睇落「呢個站淨係得九巴」，實際埋站嘅城巴路線完全唔見咗。而家：**上層淨係顯示站名**（距離縮咗做站名右邊嘅細字），**下層顯示站牌上嘅全部路線**，冇咗之前 12 條嘅截斷。同一個站唔同營辦商嘅記錄會**自動合併**（座標相距 60 米內，或者站名完全相同且相距 300 米內），路線取聯集 —— 所以一張卡可以同時有九巴同城巴。城巴有啲站未補到經緯度、根本唔會出現喺「附近」，呢啲站會靠**同名**併入同一張卡，否則佢哋永遠都唔會出現。撳張卡會開路線最多嗰個營辦商嘅站",
                "**聯營路線會同時顯示兩間巴士公司嘅班次**：101、104、111 呢類過海隧道線係九巴同城巴一齊營運 —— 同一個站牌，兩間公司各自派車、班次各自計。但兩間公司喺同一個站有各自嘅 station id，以前淨係查到其中一間，畫面睇落「呢個站淨係得九巴」，城巴嗰啲班次永遠唔會出現。而家查完自己嗰間，會再搵同一個站牌另一間公司嗰個站（同名優先，搵唔到先用座標 60 米內），把佢嘅班次撈埋入嚟**按時間排好** —— 即係見到「城巴 3 分鐘、九巴 5 分鐘」交錯顯示，先至知下一班係邊間。每張班次卡會多一個「九巴／城巴」細標籤標明邊班係邊間。判斷聯營唔係靠硬砌清單，而係用索引入面嘅資料：同一個路線號九巴同城巴都有、而且**有共同目的地**先算 —— 否則九巴 1 號同城巴 1 號（兩條完全唔同嘅線）會被誤判成一條。揀咗路線但唔係聯營線就唔會額外查，所以班次表取樣 12 個站都唔會無端端多一倍請求",
                "**主頁「附近」嘅班次卡都撈埋聯營路線嘅另一間公司**：上一項淨係做咗單站 ETA 頁、收藏頁同全線班次表，主頁仍然淨係查一間。而家主頁都一樣會撈埋同一個站牌另一間公司嘅班次，按時間交錯排好，班次卡亦會標「九巴／城巴」。主頁一次過查 12 個站，所以**加咗兩層慳請求**：淨係「呢個站本身有聯營線」嘅站先會額外查、而且最多 3 個站；查嗰陣再收窄到淨係「呢個站有嗰一兩條聯營線」，唔係全部聯營線都查 —— 所以通常淨係多一個請求，唔會令主頁變慢",
            ),
            itemsZhHans = listOf(
                "**撳版本胶囊唔再弹「发现新版本」大对话框**：以前撳底部状态栏嗰粒版本胶囊（或者入面嘅 ⟳）会即刻弹一个大对话框，入面塞住成段 Release notes 同对照表，喺电话上面要卷好耐。**而家改为静默检查**：有新版本会喺底部弹一条细提示条，写住发现新版本 vX.Y.Z 附一个「下载更新」掣，撳咗就直接下载＋校验＋开安装器；已经系最新版、或者检查失败，都只系弹一句短讯。胶囊上面三个掣（⟳ 检查更新、◷ 版本历史、▤ 复制版本号）**全部保留**，有新版本嗰阵版本号数字照旧转红。想睇完整 Release notes 就去设置页撳「检查更新」（嗰度**保留**大对话框）或者撳 ◷ 开版本历史",
                "**「附近」嘅车站卡净系显示站名同路线，而且会砌埋同一个站嘅城巴路线**：以前每张卡有一行「九巴 99 米」，即系一个站净系显示单一营办商嘅路线 —— 九巴同城巴喺同一个巴士总站各有自己嘅 station id（例如九巴系 SS667，城巴系另一个），所以画面睇落「呢个站净系得九巴」，实际埋站嘅城巴路线完全唔见咗。而家：**上层净系显示站名**（距离缩咗做站名右边嘅细字），**下层显示站牌上嘅全部路线**，冇咗之前 12 条嘅截断。同一个站唔同营办商嘅记录会**自动合并**（坐标相距 60 米内，或者站名完全相同且相距 300 米内），路线取联集 —— 所以一张卡可以同时有九巴同城巴。城巴有啲站未补到经纬度、根本唔会出现喺「附近」，呢啲站会靠**同名**并入同一张卡，否则佢哋永远都唔会出现。撳张卡会开路线最多嗰个营办商嘅站",
                "**联营路线会同时显示两间巴士公司嘅班次**：101、104、111 呢类过海隧道线系九巴同城巴一齐营运 —— 同一个站牌，两间公司各自派车、班次各自计。但两间公司喺同一个站有各自嘅 station id，以前净系查到其中一间，画面睇落「呢个站净系得九巴」，城巴嗰啲班次永远唔会出现。而家查完自己嗰间，会再搵同一个站牌另一间公司嗰个站（同名优先，搵唔到先用坐标 60 米内），把佢嘅班次捞埋入嚟**按时间排好** —— 即系见到「城巴 3 分钟、九巴 5 分钟」交错显示，先至知下一班系边间。每张班次卡会多一个「九巴／城巴」细标签标明边班系边间。判断联营唔系靠硬砌清单，而系用索引入面嘅资料：同一个路线号九巴同城巴都有、而且**有共同目的地**先算 —— 否则九巴 1 号同城巴 1 号（两条完全唔同嘅线）会被误判成一条。拣咗路线但唔系联营线就唔会额外查，所以班次表取样 12 个站都唔会无端端多一倍请求",
                "**主页「附近」嘅班次卡都捞埋联营路线嘅另一间公司**：上一项净系做咗单站 ETA 页、收藏页同全线班次表，主页仍然净系查一间。而家主页都一样会捞埋同一个站牌另一间公司嘅班次，按时间交错排好，班次卡亦会标「九巴／城巴」。主页一次过查 12 个站，所以**加咗两层悭请求**：净系「呢个站本身有联营线」嘅站先会额外查、而且最多 3 个站；查嗰阵再收窄到净系「呢个站有嗰一两条码营线」，唔系全部联营线都查 —— 所以通常净系多一个请求，唔会令主页变慢",
            ),
            itemsEn = listOf(
                "**Tapping the version pill no longer opens the big update dialog**: it used to pop a full-screen dialog with the entire release notes and comparison tables the moment you tapped the pill (or the ⟳ inside it). Now the check runs **silently**: if there is a new version, a small snackbar appears at the bottom with a **Download update** button that downloads, verifies and opens the installer; if you already have the latest version, or the check fails, you get a one-line message. All three pill actions are unchanged (⟳ check for updates, ◷ version history, ▤ copy version number), and the version number still turns red when an update exists. For the full release notes, use **Check for updates** in Settings (the big dialog stays there) or tap ◷ for version history",
                "**Nearby stop cards now show only the stop name and its routes — including Citybus**: each card used to carry a **KMB · 99 m** line, meaning one card only ever showed a single operator's routes. KMB and Citybus use different station ids for the same physical stop (KMB uses e.g. `SS667`, Citybus another), so a stop looked KMB-only even when Citybus buses actually called there. Now the **top line is just the stop name** (distance shrinks to small text on the right) and the **second line lists every route at that stop**, with the old 12-route cap removed. Records for the same physical stop across operators are **merged automatically** (within 60 m by coordinates, or within 300 m when the stop names match exactly) and their routes are unioned — so one card can list KMB and Citybus together. Some Citybus stops still have no coordinates and would never appear in **Nearby**; those are folded in **by matching stop name**, otherwise they would stay invisible forever. Tapping a card opens the operator's stop with the most routes",
                "**Jointly operated routes now show both bus operators arriving**: cross-harbour routes such as 101, 104 and 111 are run by KMB and Citybus together — same bus stop, two companies, each running its own buses on its own schedule. But the two operators use different station ids for the same physical stop, so only one of them was ever queried and a stop looked KMB-only while the Citybus departures were simply invisible. Now, after querying your own operator, the app looks up the **other operator record for the same stop sign** (matching stop name first, falling back to coordinates within 60 m) and merges its arrivals in, **sorted by time** — so you see Citybus in 3 min and KMB in 5 min interleaved and know which company the next bus belongs to. Each arrival card gets a small KMB / Citybus badge when more than one operator is present. Joint operation is not hard-coded: it is derived from the index — a route number counts only when both KMB and Citybus run it **and share a destination**, otherwise KMB route 1 and Citybus route 1 (two entirely different routes) would be mistaken for one. A selected route that is not joint is never queried extra, so building a timetable across 12 sampled stops does not double the number of requests",
                "**Home screen arrival cards also merge the other joint operator**: the previous item covered the single-stop ETA page, favourites and the full-route timetable, but Home still queried only one company. Home now merges the other operator arrivals at the same stop sign too, interleaved by time, and each card shows a small KMB / Citybus badge. Because Home queries up to 12 stops at once, requests are capped twice: only stops that actually serve a joint route are queried extra (at most 3 of them), and only the one or two joint routes present at that stop are queried rather than every joint route - so it is normally one extra request, not a doubling",
            )
        ),
        Entry(
            version = "1.1.5",
            date = "2026-10-12",
            titleZhHant = "領域核心改用 Rust",
            titleZhHans = "领域核心改用 Rust",
            titleEn = "Domain core moved into Rust",
            itemsZhHant = listOf(
                "**領域核心改用 Rust（kernel）**：⑨⑩⑪ 解決嘅係「索引放邊度、點樣查、點樣慳網絡」，但**計數邏輯**仍然喺 JVM。呢一步搬走嘅就係嗰截：沿線取樣（最多 12 個站）、砌班次表、計班次間隔、路線狀況診斷、ETA 合併去重排序、路線號正規化，全部改由 Rust 做。揀呢幾樣係因為佢哋有個共同特徵：**輸入大、輸出細、邏輯純** —— 班次表要食 12 站 × 每站 6 班 = 72 筆，淨係輸出最多 6 班，呢類「食大吐細」嘅純邏輯最啱放喺 native 度做，JVM 連中轉物件都唔使起",
                "**每一個函數都有逐行對應嘅純 Kotlin 回退**，而且算法**刻意寫到逐位一樣**（例如取樣用 `toInt()` 截斷而唔係四捨五入），所以兩邊結果必定相同 —— 設定頁會多一行「核心：Rust / kernel」定「Kotlin / fallback」。改動任何一邊，**另一邊都要跟住改**，否則就會出現「有冇 Rust 行為唔同」呢類最難 debug 嘅 bug",
                "**路線狀況而家淨係出最嚴重嗰一個**：以前可以同時出「部分站冇班次資料」＋「班次間隔偏大」兩條，而家 `diagnose` 淨係返一個碼，順序係：全線冇班次 ＞ 部分站冇資料 ＞ 間隔 ≥ 60 分鐘 ＞ 間隔 ≥ 30 分鐘 ＞ 正常。設定頁同班次表分頁都照舊睇到原因",
                "**加速層改由 Rust 內核持有**：以前 `Memo<T>` 喺 Kotlin 度持有**成個物件圖**（`List<EtaEntry>`、`List<NearbyStop>` 入面仲包住 `StopDef` 同 `List<String>`），快取入面嘅嘢一樣會畀 GC 掃到、一樣佔住 heap。而家 TTL 判斷、踢走最舊（Rust 同 Kotlin 回退**逐位一致**）、命中率統計同 payload 全部搬落 `rust/src/speed.rs`：payload 係一段**唔透明嘅字串**放喺 native heap，JVM 淨係喺**要顯示嗰一刻**先 decode 砌物件。Rust 唔使知道 ETA／班次表／附近車站嘅形狀，所以加一種新嘅快取唔使改 Rust。**一定會回退**：`.so` 唔喺度就自動行返原本嘅 Kotlin 版 `Memo`，TTL、容量、命中率口徑完全一樣。單飛（`SingleFlight`）繼續留喺 Kotlin —— 佢要 `suspend` 語義，搬落 Rust 要自己開執行緒同回呼，唔值得",
                "**索引嘅「內存與儲存」都併入 Rust 內核**：以前 Kotlin 側有兩個入口 —— `RustIndex`（索引放邊度、點樣查）同 `RustKernel`（計數），各自 `System.loadLibrary`、各自判斷 `.so` 喺唔喺度。而家**得返一個 `RustKernel`**：索引嘅二進制儲存（7.20 MB JSON → 1.04 MB）、常駐記憶體（`Box::leak` 放 native heap，Kotlin 淨係揸住一個 `Long`）、同埋四個查詢全部搬咗入去，JNI 亦一併改名做 `nativeIndex*`。Rust 側嘅索引碼由 `lib.rs` 拆咗出嚟做 `rust/src/index.rs`，同 `kernel.rs`、`speed.rs` 並列 —— 一個 `.so` 三件事，全部由內核擁有",
                "**成個 APK 嘅「記憶體與儲存」改由 Rust 內核擁有**：以前五種儲存各寫各嘅 —— 收藏用 `SharedPreferences` 塞一條 JSON 大字串、輕鐵特別班次寫一個 `.json`、ETA 同班次表快取各寫一個目錄一堆 `.json`。而家全部統一交畀 `rust/src/store.rs`：**記憶體**方面全部 KV 住喺 native heap，Kotlin 側一個位元組都唔使揸住；**磁碟**方面每個命名空間一個 `.s` 檔、**原子寫**（`.s.tmp` 再 rename），仲有 4096 條上限（超咗踢最舊寫入嗰條）同懶載入（開 App 唔會一口氣讀晒）。**一定會回退**：`.so` 唔喺度就各 store 行返原本寫法，而舊資料會喺第一次讀嗰陣**自動搬遷**落 Rust（檔案入面嘅 JSON 本身包住真正嘅抓取時間，所以搬遷唔會令時間變成而家）。設定頁會多一行「儲存：Rust / store · N 個命名空間 · M 條 · X KB」。地圖瓦片係 MB 級二進制，繼續用檔案系統 —— 塞晒落 native heap 會令低端機 OOM，唔值得"
            ),
            itemsZhHans = listOf(
                "**領域核心改用 Rust（kernel）**：⑨⑩⑪ 解決嘅係「索引放邊度、點樣查、點樣慳網絡」，但**計數邏輯**仍然喺 JVM。呢一步搬走嘅就係嗰截：沿線取樣（最多 12 個站）、砌班次表、計班次間隔、路線狀況診斷、ETA 合併去重排序、路線號正規化，全部改由 Rust 做。揀呢幾樣係因為佢哋有個共同特徵：**輸入大、輸出細、邏輯純** —— 班次表要食 12 站 × 每站 6 班 = 72 筆，淨係輸出最多 6 班，呢類「食大吐細」嘅純邏輯最啱放喺 native 度做，JVM 連中轉物件都唔使起",
                "**每一個函數都有逐行對應嘅純 Kotlin 回退**，而且算法**刻意寫到逐位一樣**（例如取樣用 `toInt()` 截斷而唔係四捨五入），所以兩邊結果必定相同 —— 設定頁會多一行「核心：Rust / kernel」定「Kotlin / fallback」。改動任何一邊，**另一邊都要跟住改**，否則就會出現「有冇 Rust 行為唔同」呢類最難 debug 嘅 bug",
                "**路線狀況而家淨係出最嚴重嗰一個**：以前可以同時出「部分站冇班次資料」＋「班次間隔偏大」兩條，而家 `diagnose` 淨係返一個碼，順序係：全線冇班次 ＞ 部分站冇資料 ＞ 間隔 ≥ 60 分鐘 ＞ 間隔 ≥ 30 分鐘 ＞ 正常。設定頁同班次表分頁都照舊睇到原因",
                "**加速層改由 Rust 內核持有**：以前 `Memo<T>` 喺 Kotlin 度持有**成個物件圖**（`List<EtaEntry>`、`List<NearbyStop>` 入面仲包住 `StopDef` 同 `List<String>`），快取入面嘅嘢一樣會畀 GC 掃到、一樣佔住 heap。而家 TTL 判斷、踢走最舊（Rust 同 Kotlin 回退**逐位一致**）、命中率統計同 payload 全部搬落 `rust/src/speed.rs`：payload 係一段**唔透明嘅字串**放喺 native heap，JVM 淨係喺**要顯示嗰一刻**先 decode 砌物件。Rust 唔使知道 ETA／班次表／附近車站嘅形狀，所以加一種新嘅快取唔使改 Rust。**一定會回退**：`.so` 唔喺度就自動行返原本嘅 Kotlin 版 `Memo`，TTL、容量、命中率口徑完全一樣。單飛（`SingleFlight`）繼續留喺 Kotlin —— 佢要 `suspend` 語義，搬落 Rust 要自己開執行緒同回呼，唔值得",
                "**索引的「内存与存储」都并入 Rust 内核**：以前 Kotlin 侧有两个入口 —— `RustIndex`（索引放哪里、怎么查）同 `RustKernel`（计数），各自 `System.loadLibrary`、各自判断 `.so` 在不在。现在**只剩一个 `RustKernel`**：索引的二进制存储（7.20 MB JSON → 1.04 MB）、常驻内存（`Box::leak` 放 native heap，Kotlin 只拿一个 `Long`）、还有四个查询全部搬了进去，JNI 也一并改名为 `nativeIndex*`。Rust 侧的索引码由 `lib.rs` 拆出去做 `rust/src/index.rs`，同 `kernel.rs`、`speed.rs` 并列 —— 一个 `.so` 三件事，全部由内核拥有",
                "**整个 APK 的「内存与存储」改由 Rust 内核拥有**：以前五种存储各写各的 —— 收藏用 `SharedPreferences` 塞一条 JSON 大字符串、轻铁特别班次写一个 `.json`、ETA 同班次表缓存各写一个目录一堆 `.json`。现在全部统一交给 `rust/src/store.rs`：**内存**方面全部 KV 住在 native heap，Kotlin 侧一个字节都不用拿住；**磁盘**方面每个命名空间一个 `.s` 文件、**原子写**（`.s.tmp` 再 rename），还有 4096 条上限（超了踢最旧写入那条）同懒加载（开 App 不会一口气读齐）。**一定会回退**：`.so` 不在就各 store 行返原本写法，而旧数据会在第一次读的时候**自动迁移**落 Rust（文件里面的 JSON 本身包住真正的抓取时间，所以迁移不会令时间变成现在）。设置页会多一行「储存：Rust / store · N 个命名空间 · M 条 · X KB」。地图瓦片是 MB 级二进制，继续用文件系统 —— 全部塞落 native heap 会令低端机 OOM，不值得",
            ),
            itemsEn = listOf(
                "**Domain core moved into Rust (kernel)**: ⑨⑩⑪ fixed *where the index lives, how it's queried, and how the network is saved* — but the **computation** still ran on the JVM. This step moves it: route sampling (max 12 stops), trip-table build, headway, route condition diagnosis, ETA merge/dedupe/sort and route-number normalisation now all run in Rust. These were picked for one shared trait: **big input, small output, pure logic** — the trip table eats 12 stops × 6 arrivals = 72 entries and emits at most 6, exactly the kind of work worth doing natively, where the JVM does not even allocate the intermediate objects",
                "**Every function has a line-for-line pure-Kotlin fallback**, and the algorithms are *deliberately* written to match bit for bit (sampling truncates with `toInt()` rather than rounding), so both sides always agree — Settings gains a line showing either \"Kernel: Rust / kernel\" or \"Kotlin / fallback\". Change one side and **you must change the other**, otherwise you get the worst class of bug: behaviour that differs depending on whether the `.so` exists",
                "**Route condition now reports only the single most severe issue**: it could previously emit both \"some stops have no data\" and \"headway unusually large\" side by side. `diagnose` now returns one code, ordered: no service at all > some stops missing > gap ≥ 60 min > gap ≥ 30 min > normal. Settings and the timetable tab still show the reason",
                "**The speed layer is now owned by the Rust kernel**: `Memo<T>` used to hold the **entire object graph** on the Kotlin side (`List<EtaEntry>`, and `List<NearbyStop>` wrapping a `StopDef` plus a `List<String>`), so cached entries were still walked by the GC and still sat on the heap. Now the TTL checks, oldest-first eviction, hit/miss counters and the payload itself all live in `rust/src/speed.rs`: the payload is an **opaque string** in native memory and the JVM only decodes it at the moment something is displayed. Rust never needs to know the shape of an ETA, a trip table or a nearby stop, so adding a new cache needs no Rust change. **It always falls back**: without the `.so` the original Kotlin `Memo` runs, with identical TTLs, capacities and hit/miss accounting. `SingleFlight` stays in Kotlin on purpose - it needs `suspend` semantics, and moving it would mean hand-rolling threads and callbacks",
                "**Index memory and storage are now part of the Rust kernel**: Kotlin used to have two entry points - `RustIndex` (where the index lives, how it is queried) and `RustKernel` (computation) - each doing its own `System.loadLibrary` and its own check for whether the `.so` is there. Now there is **one `RustKernel`**: the binary index format (7.20 MB JSON -> 1.04 MB), the resident index (`Box::leak`, native heap, Kotlin holds only a `Long`) and all four queries moved into it, with the JNI symbols renamed to `nativeIndex*`. On the Rust side the index code moved out of `lib.rs` into `rust/src/index.rs`, sitting next to `kernel.rs` and `speed.rs` - one `.so`, three jobs, all owned by the kernel",
                "**Memory and storage for the whole APK are now owned by the Rust kernel**: five stores used to each roll their own - favourites crammed a giant JSON string into `SharedPreferences`, light-rail special trips wrote one `.json`, and the ETA and trip-table caches each wrote a directory of `.json` files. All of them now go through `rust/src/store.rs`: in **memory** every KV pair lives on the native heap, so Kotlin holds not a single byte; on **disk** each namespace is one `.s` file written **atomically** (`.s.tmp` then rename), with a 4096-entry cap (oldest write evicted first) and lazy loading so startup does not read everything at once. **It always falls back**: without the `.so` each store uses its original code, and existing data is **migrated** into Rust on first read (the stored JSON already carries the real fetch timestamp, so migrating does not rewrite it to \"now\"). Settings gains a line: \"Storage: Rust / store · N namespaces · M entries · X KB\". Map tiles stay on the filesystem on purpose - they are multi-MB binary blobs, and pinning them all in native memory would OOM low-end phones",
            )
        ),
        Entry(
            version = "1.1.4",
            date = "2026-10-10",
            titleZhHant = "班次淨顯示一個方向，冇網都睇到",
            titleZhHans = "班次只显示一个方向，没网也能看",
            titleEn = "One direction at a time, and it works offline",
            itemsZhHant = listOf(
                "**加咗「官方時間表」連結**（解決「官方紙本時間表冇得顯示」）：查證過政府公開數據平台（data.gov.hk）**冇發車時間表** —— 運輸署路線資料嘅官方 data spec 淨係得 `serviceMode` 同 `journeyTime`，冇服務時間／班次／頭尾班車欄位；各營辦商嘅官方 ETA 接口亦只提供即時到站。唯一相關嘅係指去官方網頁嘅「班次連結」。所以新增 `OfficialTimetable`：專線小巴會**深鏈到運輸署官方路線頁**（`get_gmb_detail.php?route_id=…&lang=TC/SC/EN`，該頁有完整官方時間表）；其他營辦商就去官方路線查詢頁。**一律唔會自己編數據，亦唔會畀個未驗證嘅深鏈**",
                "**班次淨係顯示一個方向**（解決「同一個站兩個方向撈埋一齊」）：官方接口會一次過返晒來回兩個方向（例如 38A 往荃灣 ＋ 38A 往美孚），以前全部撈埋顯示，要逐張卡睇目的地先分得清。**而家預設淨係顯示最快嗰班嘅方向**；想睇第個方向就撳上面嘅方向掣切換，想一次過睇晒就揀「全部方向」。得一個方向嘅路線唔會顯示切換掣，唔會阻地方",
                "**真實地圖而家可以完全離線**（解決「第一次之後都要聯網攞 JS」）：以前就算瓦片有離線包，每次開地圖都仲要上 `unpkg.com` 攞 Leaflet 嘅 JS／CSS —— 一冇網就載唔到，直接退回示意圖。而家新增 `AssetCache`：第一次成功攞到就存落機並校驗內容（CSS 要有 `.leaflet-`、JS 要有 `L.map`，錯誤頁唔會當正貨），之後攔截請求直接用本地副本。所以**睇過一次地圖之後，飛機模式都出到真實地圖**",
                "**班次表有離線快取**（解決「班次表一定要聯網」）：每條路線最新一次成功抓到嘅班次表會存落機。有網照舊抓新嘅；**冇網或者抓取失敗就自動顯示上次嗰份**，並用紅字標明「離線資料 · HH:mm 抓取（唔係即時班次）」，唔會再淨係彈一句「要連網先睇到」",
                "**冇網都睇到班次**（解決「一冇網就淨低個空畫面」）：新增 `EtaCache`，記低每一次成功抓到嘅 ETA。有網照舊抓新嘅；**冇網或者官方接口冇回應就自動顯示上次嗰份**，車站頁會用紅字標明「冇網／官方接口冇回應，顯示嘅係 HH:mm 嘅資料」—— 寧願講明係幾點嘅資料，都唔好扮即時",
                "**桌面小工具都有離線備援**：Widget 以前拉唔到 ETA 就全部變「—」。而家會退返上次成功嗰筆，並喺分鐘後面加「離線 · N 分前」，地鐵入面或者飛機模式都唔會再淨低個空 Widget",
                "修好「城巴／小巴深度索引完成：新增 0 個車站」嘅問題：以前 `suspendCatching` 包住晒成條路線嘅抓取，只要其中**一個站**嘅 `/stop` 請求失敗或者超時，成條路線嘅路線-車站對應都會被一齊丟棄 —— 城巴每條路線有幾十個站，撞中一次就全條冇咗，所以最後顯示「新增 0」。而家**分兩層容錯**：路線-車站對應抓唔到先算失敗，車站座標逐個自己容錯，一個站失敗淨係少一個站；顯示嘅數字亦改為**真正新加**嘅數量（去重後再扣走索引已經有嘅），並會同時顯示成功路線數；如果全部路線都抓唔到，會明確講「全部抓唔到，請檢查網絡」而唔係扮「新增 0」",
                "**「新增 0 個車站」唔會再誤導**：以前只要城巴 `/stop` 請求全部失敗（官方接口繁忙或者限流），畫面都淨係出「新增 0 個車站」，令人以為「已經補完、冇嘢要補」。而家會數住失敗次數：如果補完咗路線但一個車站座標都冇抓到，會明確講「補完 N 條路線，但 M 個車站座標抓唔到（官方接口繁忙或限流），請再撳一次」",
                "**修好「索引診斷淨係顯示 `${'$'}msg`」嘅問題**：設定頁「索引診斷」嗰行嘅字串插值寫錯咗，變咗原樣輸出 `${'$'}msg`，睇唔到城巴／小巴到底咩事。而家會正確顯示原因（例如「城巴官方冇全量車站接口，車站需另行修復」）。**順帶修埋「診斷修好咗都唔會消失」**：診斷以前淨係喺建索引時「加」，修好之後唔會清，所以「需要修復」會永久掛住；而家一律按**目前索引嘅實際狀態**重算，補到車站／路線就會自動消失",
                "**索引改為用 Rust 存（可選加速層）**（解決「索引 JSON 7 MB、字串重複」）：新增 `rust/` 嘅 Rust 索引庫 —— 緊湊二進制格式 ＋ 字串去重（interning）。實測（用真實 7.20 MB 索引）：**磁碟 7.20 MB → 1.23 MB（17%）**，25771 條字串全部共用同一個實例；附近車站同車站→路線嘅查詢結果同純 Kotlin 版**完全一致**。**一定會回退**：`.so` 唔存在或者加載失敗就行返原本嘅 JSON 路徑，功能完全一樣，所以本地 build 冇裝 Rust toolchain 都冇問題",
            "**索引改為由 Rust 完全擁有（記憶體 ＋ 儲存）**：以前 Rust 淨係做「儲存」，Kotlin 攞完 handle 會砌返一個 `IndexData` 落 JVM heap，即係 JVM 仍然有份完整副本。而家索引由 Rust 放喺 **native heap**（常駐登錄表），Kotlin 淨係揸住一個 `Long`；搜尋路線、搜尋車站、附近車站、路線嘅車站**全部喺 Rust 側做，淨係把要顯示嗰幾十筆傳過界**，JVM heap 唔使再持有成個索引。儲存亦統一為二進制：舊 JSON 會喺第一次開啟時自動遷移做二進制再刪走，從此磁碟上淨係得 1.23 MB 嘅二進制。`.so` 唔喺度／常駐失敗一律自動行返純 Kotlin 路徑，功能完全一樣",
                "**新增加速層**（解決「每次入車站頁都要對住轉圈等」）：以前無論幾時入去都一定打一次網絡，就算 3 秒前先睇過都係咁。而家加咗 TTL 快取 ＋ stale-while-revalidate：有新鮮嘅**即出**（連轉圈都唔使），得舊嘅就**先出舊嘅再背景刷新**，完全冇先轉圈；同一個站同時淨係一個請求（快速撳幾下唔會開幾個連線）；開 App 會背景預熱收藏站嘅 ETA。TTL 係照數據特性揀嘅：ETA 20 秒（官方本身就係 20–30 秒先變一次）、班次表 90 秒（要打 12 個站，成本最高）、附近車站 45 秒（自動定位每 60 秒重整，唔會用到過期嘢）。**呢層純 Kotlin，Rust 有冇 build 到都一樣生效**；設定頁「索引統計」下面會顯示命中率"
            ),
            itemsZhHans = listOf(
                "**加了「官方时间表」链接**（解决「官方纸本时间表无法显示」）：查证过政府公开数据平台（data.gov.hk）**没有发车时间表** —— 运输署路线资料的官方 data spec 只有 `serviceMode` 和 `journeyTime`，没有服务时间／班次／头尾班车字段；各营运商的官方 ETA 接口也只提供实时到站。唯一相关的是指向官方网页的「班次链接」。所以新增 `OfficialTimetable`：专线小巴会**深链到运输署官方路线页**（`get_gmb_detail.php?route_id=…&lang=TC/SC/EN`，该页有完整官方时间表）；其他营运商就去官方路线查询页。**一律不会自己编数据，也不会给未验证的深链**",
                "**班次只显示一个方向**（解决「同一个站两个方向混在一起」）：官方接口会一次过返回来回两个方向（例如 38A 往荃湾 ＋ 38A 往美孚），以前全部混在一起显示，要逐张卡看目的地才分得清。**现在默认只显示最快那班的方向**；想看另一个方向就按上面的方向按钮切换，想一次过看全部就选「全部方向」。只有一个方向的路线不会显示切换按钮，不会占地方",
                "**真实地图现在可以完全离线**（解决「第一次之后都要联网取 JS」）：以前就算瓦片有离线包，每次开地图都还要上 `unpkg.com` 取 Leaflet 的 JS／CSS —— 一没网就载不到，直接退回示意图。现在新增 `AssetCache`：第一次成功取到就存到本机并校验内容（CSS 要有 `.leaflet-`、JS 要有 `L.map`，错误页不会当正货），之后拦截请求直接用本地副本。所以**看过一次地图之后，飞行模式都能显示真实地图**",
                "**班次表有离线缓存**（解决「班次表一定要联网」）：每条路线最新一次成功抓到的班次表会存到本机。有网照旧抓新的；**没网或者抓取失败就自动显示上次那份**，并用红字标明「离线资料 · HH:mm 抓取（不是实时班次）」，不会再只弹一句「要联网才看得到」",
                "**没网也能看到班次**（解决「一没网就只剩空画面」）：新增 `EtaCache`，记下每一次成功抓到的 ETA。有网照旧抓新的；**没网或者官方接口没回应就自动显示上次那份**，车站页会用红字标明「没网／官方接口没回应，显示的是 HH:mm 的资料」—— 宁愿讲明是几点的资料，也不要装作实时",
                "**桌面小工具也有离线备用**：Widget 以前拉不到 ETA 就全部变「—」。现在会退回上次成功那笔，并在分钟后面加「离线 · N 分前」，地铁里面或者飞行模式都不会再只剩空 Widget",
                "修好「城巴／小巴深度索引完成：新增 0 个车站」的问题：以前 `suspendCatching` 包住了整条路线的抓取，只要其中**一个站**的 `/stop` 请求失败或者超时，整条路线的路线-车站对应都会被一起丢弃 —— 城巴每条路线有几十个站，撞中一次就整条没了，所以最后显示「新增 0」。现在**分两层容错**：路线-车站对应抓不到才算失败，车站坐标逐个自己容错，一个站失败只是少一个站；显示的数字也改为**真正新增**的数量（去重后再扣掉索引已有的），并会同时显示成功路线数；如果全部路线都抓不到，会明确说「全部抓不到，请检查网络」而不是装作「新增 0」",
                "**「新增 0 个车站」不会再误导**：以前只要城巴 `/stop` 请求全部失败（官方接口繁忙或者限流），画面都只出「新增 0 个车站」，让人以为「已经补完、没东西要补」。现在会记着失败次数：如果补完了路线但一个车站坐标都没抓到，会明确讲「补完 N 条路线，但 M 个车站坐标抓不到（官方接口繁忙或限流），请再点一次」",
                "**修好「索引诊断只显示 `${'$'}msg`」的问题**：设置页「索引诊断」那一行的字符串插值写错了，变成原样输出 `${'$'}msg`，看不到城巴／小巴到底什么事。现在会正确显示原因（例如「城巴官方没有全量车站接口，车站需另行修复」）。**顺带修好「诊断修好了也不会消失」**：诊断以前只在建索引时「加」，修好后不会清，所以「需要修复」会一直挂着；现在一律按**目前索引的实际状态**重算，补到车站／路线就会自动消失",
                "**索引改为用 Rust 存（可选加速层）**（解决「索引 JSON 7 MB、字符串重复」）：新增 `rust/` 的 Rust 索引库 —— 紧凑二进制格式 ＋ 字符串去重（interning）。实测（用真实 7.20 MB 索引）：**磁盘 7.20 MB → 1.23 MB（17%）**，25771 条字符串全部共用同一个实例；附近车站和车站→路线的查询结果和纯 Kotlin 版**完全一致**。**一定会回退**：`.so` 不存在或者加载失败就走回原本的 JSON 路径，功能完全一样，所以本地 build 没装 Rust toolchain 也没问题",
            "**索引改为由 Rust 完全拥有（内存 ＋ 存储）**：以前 Rust 只是做「存储」，Kotlin 拿到 handle 会砌一个 `IndexData` 落 JVM heap，即系 JVM 仍然有份完整副本。现在索引由 Rust 放在 **native heap**（常驻登录表），Kotlin 只是拿住一个 `Long`；搜索路线、搜索车站、附近车站、路线的车站**全部在 Rust 侧做，只是把要显示那几十笔传过界**，JVM heap 不用再持有整个索引。存储亦统一为二进制：旧 JSON 会在第一次开启时自动迁移做二进制再删走，从此磁盘上只剩 1.23 MB 的二进制。`.so` 不在／常驻失败一律自动行返纯 Kotlin 路径，功能完全一样",
                "**新增加速层**（解决「每次进车站页都要对着转圈等」）：以前无论什么时候进去都一定打一次网络，就算 3 秒前刚看过也是这样。现在加了 TTL 缓存 ＋ stale-while-revalidate：有新鲜的**立即显示**（连转圈都不用），只有旧的就**先显示旧的再后台刷新**，完全没有才转圈；同一个站同时只有一个请求（快速按几下不会开几个连接）；开 App 会后台预热收藏站的 ETA。TTL 是按数据特性选的：ETA 20 秒（官方本身就是 20–30 秒才变一次）、班次表 90 秒（要打 12 个站，成本最高）、附近车站 45 秒（自动定位每 60 秒重整，不会用到过期的）。**这层纯 Kotlin，Rust 有没有 build 到都一样生效**；设置页「索引统计」下面会显示命中率"
            ),
            itemsEn = listOf(
                "**Added an \"Official timetable\" link** (fixes \"printed timetables can't be shown\"): confirmed that data.gov.hk has **no departure timetable** - the official data spec for the Transport Department route dataset only has `serviceMode` and `journeyTime`, with no service-hours, headway, or first/last-bus fields, and every operator's official ETA endpoint gives live arrivals only. The sole related field is a \"headway link\" pointing at the operator's own page. So a new `OfficialTimetable` deep-links **GMB routes straight to the TD's official route page** (`get_gmb_detail.php?route_id=...&lang=TC/SC/EN`, which carries a full official timetable); other operators open their official route-search page. **No data is invented and no unverified deep link is offered**",
                "**Arrivals now show one direction at a time** (fixes \"both directions jumbled together\"): the operator returns both directions at once (e.g. 38A to Tsuen Wan plus 38A to Mei Foo), and they used to be interleaved so you had to read the destination on every card. **By default only the direction of the soonest trip is shown**; tap a direction chip to switch, or pick \"All directions\" to see everything at once. Routes with only one direction don't show the chips, so nothing is wasted",
                "**The real map now works fully offline** (fixes \"still needed the network for JS after the first view\"): even with the tile pack in place, every map open still fetched Leaflet's JS/CSS from `unpkg.com`, so offline it failed and fell back to the schematic. A new `AssetCache` caches both files on first success and validates them (CSS must contain `.leaflet-`, JS must contain `L.map`, so error pages are rejected), then serves them by intercepting the request. Result: **after viewing a map once, it renders fully in airplane mode**",
                "**The trip table has an offline cache** (fixes \"the trip table always needed a network\"): the last successfully fetched table for each route is stored on device. Online it still fetches fresh data; **offline or on failure it falls back to the last one**, clearly labelled in red as \"Offline data, fetched at HH:mm, not live\" instead of just saying a network is required",
                "**Arrivals still show without a network** (fixes \"one lost signal and you get an empty screen\"): a new `EtaCache` stores every successful fetch. Online it fetches fresh as usual; **offline, or when the operator doesn't answer, it falls back to the last good data**, and the stop page labels it in red, saying which hour and minute the data is from. Better to say when the data is from than to pretend it's live",
                "**The home-screen widget has an offline fallback too**: it used to show a column of dashes whenever arrivals couldn't be fetched. It now falls back to the last successful reading and appends \"Offline, N min ago\", so you don't get an empty widget in the MTR or in airplane mode",
                "Fixed the Citybus and GMB deep index reporting 0 stops added: the whole fetch for a route was wrapped in a single `suspendCatching`, so if the `/stop` request for just **one** of its dozens of stops failed or timed out, that route's entire stop list was discarded along with it - one bad request wiped out the whole route, which is why the total came out as zero. There are now **two layers of fault tolerance**: a route only counts as failed when its route-stop list cannot be fetched, while stop coordinates are fetched individually so one bad stop costs only that stop. The number reported is now the count **actually added** (deduplicated, minus stops already in the index), shown alongside how many routes were fetched; and when every route fails it says so explicitly instead of reporting 0",
                "**\"0 stops added\" no longer misleads**: if every Citybus `/stop` request failed (endpoint busy or rate-limited), the app still showed \"0 stops added\", which reads like nothing is left to fix. It now counts failures: if routes were mapped but no stop coordinates came back, it says clearly that N routes were mapped but M stop coordinates failed (endpoint busy or rate-limited) and asks you to tap again",
                "**Fixed the index diagnostic showing a literal `${'$'}msg`**: the string interpolation on the Settings \"Index diagnostic\" line was written wrong, so it printed `${'$'}msg` verbatim and you could never see what was actually wrong with Citybus or GMB. It now shows the real reason (e.g. \"Citybus has no bulk-stop API - stops need a separate repair\"). **Also fixed \"the diagnostic never clears once fixed\"**: diagnostics were only ever *added* while building the index and never removed, so \"needs repair\" hung around forever; they are now recomputed from the **current index state**, so they disappear as soon as stops or routes are actually in place",
                "**The index can now be stored in Rust (optional accelerator)** (fixes 「a 7 MB JSON index with duplicated strings」): a new Rust crate under `rust/` provides a compact binary format plus string interning. Measured on a real 7.20 MB index: **7.20 MB on disk becomes 1.23 MB (17%)**, with all 25771 strings shared as single instances; nearby-stop and stop-to-route queries return results **identical to the pure-Kotlin version**. **It always falls back**: if the `.so` is missing or fails to load, the original JSON path is used and nothing changes functionally, so a local build without the Rust toolchain is fine",
            "**The index is now fully owned by Rust (memory + storage)**: Rust used to handle storage only — Kotlin took the handle and rebuilt an `IndexData` on the JVM heap, so the JVM still held a complete copy. Now the index lives in **native heap** (a resident registry) and Kotlin holds nothing but a `Long`; route search, stop search, nearby stops and a route's stops **all run on the Rust side and only the few dozen rows being displayed cross the boundary**, so the JVM heap no longer holds the whole index. Storage is binary-only too: any legacy JSON is migrated to binary on first open and then deleted, leaving just the 1.23 MB binary on disk. If the `.so` is missing or residency fails, everything falls back to the pure-Kotlin path with identical behaviour",
                "**Added a speed layer** (fixes 「every visit to a stop page means watching a spinner」): previously every single visit hit the network, even if you'd looked 3 seconds earlier. Now there's a TTL cache with stale-while-revalidate: fresh data **shows instantly** (no spinner at all); if only stale data exists it **shows that first and refreshes in the background**; only a complete miss spins. One in-flight request per stop, so tapping repeatedly no longer opens several connections, and favourites' arrivals are warmed in the background at startup. TTLs are chosen per data type: arrivals 20s (the official feed only changes every 20–30s anyway), trip tables 90s (12 stop requests — the most expensive), nearby stops 45s (auto-locate re-runs every 60s, so it never serves stale data). **This layer is pure Kotlin and works whether or not the Rust `.so` was built**; the hit rate is shown under Settings ▸ Index statistics"
            ),
        ),

        Entry(
            version = "1.1.3.1",
            date = "2026-10-10",
            titleZhHant = "撳版本膠囊即睇更新內容",
            titleZhHans = "按版本胶囊即看更新内容",
            titleEn = "Tap the version pill to see what's new",
            itemsZhHant = listOf(
                "設定頁撳版本膠囊（或者撳 ⟳）而家會**彈出「檢查更新」對話框**（跟 code-to-app 原版行為）：對話框入面有版本膠囊、最新版本／目前版本、APK 大小、Release notes，底部係「關閉」同「下載」兩個掣；撳「下載」直接喺 App 內開始下載。以前撳完淨係喺膠囊下面換行狀態文字，睇唔到更新內容",
                "修好**撳底部狀態欄嗰個版本膠囊會無啦啦跳去收藏頁**嘅問題：以前係 `if (hasUpdate) onCheckUpdate() else onOpenFavorites()` —— 即係冇新版嗰陣撳落去淨係跳頁，永遠唔會顯示更新內容。而家三處膠囊（底部狀態欄 / 設定頁 / 關於頁）**一律撳落去就檢查更新並彈出「檢查更新」對話框**",
                "技術：版本號比較以前淨睇 3 段（`1.1.3.1` 會被當成 `1.1.3`），會令第 4 段嘅版本永遠唔會彈「有新版本」。而家 `Version` 支援第 4 段，`1.1.3.1` 正確咁新過 `1.1.3`；舊嘅三段同 `-beta` 寫法完全唔受影響"
            ),
            itemsZhHans = listOf(
                "设置页按版本胶囊（或者按 ⟳）现在会**弹出「检查更新」对话框**（跟 code-to-app 原版行为）：对话框里面有版本胶囊、最新版本／当前版本、APK 大小、Release notes，底部是「关闭」和「下载」两个按钮；按「下载」直接在 App 内开始下载。以前按完只是在胶囊下面换一行状态文字，看不到更新内容",
                "修好**按底部状态栏那个版本胶囊会无缘无故跳到收藏页**的问题：以前是 `if (hasUpdate) onCheckUpdate() else onOpenFavorites()` —— 也就是没有新版本时按下去只是跳页，永远不会显示更新内容。现在三处胶囊（底部状态栏 / 设置页 / 关于页）**一律按下去就检查更新并弹出「检查更新」对话框**",
                "技术：版本号比较以前只看 3 段（`1.1.3.1` 会被当成 `1.1.3`），会让第 4 段的版本永远不弹「有新版本」。现在 `Version` 支持第 4 段，`1.1.3.1` 正确地新于 `1.1.3`；旧的三段和 `-beta` 写法完全不受影响"
            ),
            itemsEn = listOf(
                "Tapping the version pill on the Settings page (or the ⟳ icon) now **opens the Check for updates dialog** (matching code-to-app): it shows the version pill, latest and current version, APK size and release notes, with Close and Download at the bottom \u2014 and Download starts the in-app download. Previously it only swapped the status line under the pill, so you never saw what was new",
                "Fixed the **bottom-bar version pill jumping to Favourites instead of showing updates**: it read `if (hasUpdate) onCheckUpdate() else onOpenFavorites()`, so with no update pending the press only navigated away and you never saw the release notes. All three pills (bottom bar / Settings / About) now **always check for updates and open the Check for updates dialog**",
                "Technical: version comparison only read 3 segments, so `1.1.3.1` collapsed to `1.1.3` and a fourth-segment release would never trigger the \"update available\" prompt. `Version` now reads the 4th segment, so `1.1.3.1` is correctly newer than `1.1.3`; existing three-segment and `-beta` forms are unaffected"
            ),
        ),

        Entry(
            version = "1.1.3",
            date = "2026-10-09",
            titleZhHant = "路線號打得齊 + App 名簡化",
            titleZhHans = "路线号打得齐 + App 名简化",
            titleEn = "Route entry fixed and app name simplified",
            itemsZhHant = listOf(
                "搜尋路線號而家支援「最多 3 個數字 + 最多 2 個英文」：鍵盤字母由 9 個補到 15 個（A B C D E H K / M N P R S T W X），以前 1D、2H、51M、2E、P960、W3、R11、T6 呢啲因為無 D、H、M、P、W、R、T 而**根本打唔到**；而家全部打到",
                "輸入框加咗長度上限：**最多 3 個數字 + 最多 2 個英文**（合計 5 個字元）。以前冇上限，可以一直撳落去砌個永遠搜唔到嘅長字串；而家會數住數字同英文嘅數量，砌唔出嘅組合唔會再接受（例如 1234 淨收 123、1ABC 淨收 1AB）",
                "輸入框提示改為「輸入路線號碼，如 1A、6、K51、N691」，一眼就知可以點打",
                "App 名簡化：由「HKATE 巴士到站預報」改為淨係「HKATE」（桌面圖示下面、關於頁都係）；順手修好設定頁版本號硬編碼顯示「1.0.0」嘅 bug，而家會顯示實際版本",
                "修好「連撳會食字、結果淨係入到一個數字」嘅 bug：以前鍵盤係 `vm.setQuery(query + it)`，`query` 係撳嗰刻**畫面上**嘅值；而 setQuery 會即時過濾 2099 條路線（喺主線程）拖慢重組，連撳兩下第二下會讀到舊值，「1」再撳「2」變咗「2」。而家改為 `appendQueryChar()`，喺 ViewModel 直接讀 `_query.value`（一定係最新值），連撳幾快都唔會食字；刪除鍵同理",
                "更新功能改為**完全喺 App 內下載**，唔再開瀏覽器：設定頁同版本歷史嘅「下載」掣以前係 `openUrl()` 開外部瀏覽器，而家統一改為 `vm.downloadAndInstall()` —— App 內下載 APK、顯示進度條（%／MB）、校驗 SHA-256，下載完個掣變「安裝」直接開系統安裝器；撳掣可以取消，失敗可以重試"
            ),
            itemsZhHans = listOf(
                "搜索路线号现在支持「最多 3 个数字 + 最多 2 个英文」：键盘字母由 9 个补到 15 个（A B C D E H K / M N P R S T W X），以前 1D、2H、51M、2E、P960、W3、R11、T6 这些因为没有 D、H、M、P、W、R、T 而**根本打不到**；现在全部打得到",
                "输入框加了长度上限：**最多 3 个数字 + 最多 2 个英文**（合计 5 个字符）。以前没有上限，可以一直按下去拼出一个永远搜不到的长字符串；现在会数着数字和英文的数量，拼不出的组合不会再接受（例如 1234 只收 123、1ABC 只收 1AB）",
                "输入框提示改为「输入路线号码，如 1A、6、K51、N691」，一眼就知道可以怎么打",
                "App 名简化：由「HKATE 巴士到站预报」改为只有「HKATE」（桌面图标下面、关于页都是）；顺手修好设置页版本号硬编码显示「1.0.0」的 bug，现在会显示实际版本",
                "修好「连按会吞字、结果只能输入一个数字」的 bug：以前键盘是 `vm.setQuery(query + it)`，`query` 是按下那一刻**画面上**的值；而 setQuery 会即时过滤 2099 条路线（在主线程）拖慢重组，连按两下第二下会读到旧值，「1」再按「2」变成了「2」。现在改为 `appendQueryChar()`，在 ViewModel 直接读 `_query.value`（一定是最新的值），连按多快都不会吞字；删除键同理",
                "更新功能改为**完全在 App 内下载**，不再开浏览器：设置页和版本历史的「下载」按钮以前是 `openUrl()` 打开外部浏览器，现在统一改为 `vm.downloadAndInstall()` —— 在 App 内下载 APK、显示进度条（%／MB）、校验 SHA-256，下载完按钮变「安装」直接打开系统安装器；可以取消，失败可以重试"
            ),
            itemsEn = listOf(
                "Route-number entry now supports up to 3 digits plus 2 letters. The keyboard went from 9 letters to 15 (A B C D E H K / M N P R S T W X) \u2014 routes like 1D, 2H, 51M, 2E, P960, W3, R11 and T6 were previously **impossible to type** because D, H, M, P, W, R and T were missing",
                "The field now caps input at **3 digits and 2 letters** (5 characters). Nothing used to stop you building an endlessly long string that could never match; the field counts digits and letters and rejects impossible combinations (e.g. 1234 keeps only 123, 1ABC keeps only 1AB)",
                "The field hint now reads \u201cRoute no., e.g. 1A, 6, K51, N691\u201d so the accepted format is obvious at a glance",
                "Simplified the app name: \u201cHKATE \u2013 HK bus arrivals\u201d becomes just \u201cHKATE\u201d (both under the launcher icon and on the About page). Also fixed the Settings page hard-coding version 1.0.0 \u2014 it now shows the real version",
                "Fixed characters being swallowed on fast key presses (which made it look like you could only type one digit): the keyboard called `vm.setQuery(query + it)` where `query` was the value **on screen** at that moment, and setQuery filters 2,099 routes on the main thread, delaying recomposition \u2014 so a second quick press still read the stale value and \u201c1\u201d then \u201c2\u201d became just \u201c2\u201d. It now calls `appendQueryChar()`, which reads `_query.value` inside the ViewModel (always current), so no press is lost however fast you type; the delete key works the same way",
                "Updates now download **entirely inside the app** \u2014 no browser: the Download buttons on the Settings page and in version history used to call `openUrl()` and hand off to an external browser; they now go through `vm.downloadAndInstall()`, which fetches the APK in-app with a progress bar (%/MB), verifies SHA-256, then turns the button into Install to open the system installer. Tapping again cancels, and failures can be retried"
            ),
        ),

        Entry(
            version = "1.1.2",
            date = "2026-10-09",
            titleZhHant = "效能大優化：開 App 唔再卡",
            titleZhHans = "效能大优化：开 App 不再卡",
            titleEn = "Major performance work: app start no longer stalls",
            itemsZhHant = listOf(
                "效能：索引（約 7 MB JSON）嘅讀寫以前喺**主線程**做，開 App 會卡住（實測 load 187 ms + save 342 ms，低階手機估計約 2.6 秒）。而家改喺 IO 線程、用流式讀寫、寫檔改為原子替換（寫到一半崩潰都唔會損壞索引）",
                "效能：附近車站以前每揾一個站都要掃晒全部 63150 條路線-車站對應（50 個站 = 約 315 萬次），而家建成一次索引表，之後 O(1) 查表；實測路線掃描由 454 萬次減到 6.3 萬次，快取命中後每次重整嘅計算由 352 ms 減到 6 ms（約 58 倍）"
            ),
            itemsZhHans = listOf(
                "性能：索引（约 7 MB JSON）的读写以前在**主线程**做，开 App 会卡住（实测 load 187 ms + save 342 ms，低端手机估计约 2.6 秒）。现在改在 IO 线程、用流式读写、写档改为原子替换（写到一半崩溃都不会损坏索引）",
                "性能：附近车站以前每找一个站都要扫完所有 63150 条路线-车站对应（50 个站 = 约 315 万次），现在建成一次索引表，之后 O(1) 查表；实测路线扫描由 454 万次减到 6.3 万次，缓存命中后每次重整的计算由 352 ms 减到 6 ms（约 58 倍）"
            ),
            itemsEn = listOf(
                "Performance: the ~7 MB index JSON was read and written \u201c**on the main thread**\u201d, stalling app start (measured 187 ms load + 342 ms save; roughly 2.6 s on a low-end phone). It now runs on the IO thread with streaming codecs and an atomic temp-file rename, so a crash mid-write can no longer corrupt the index",
                "Performance: finding nearby stops used to scan all 63,150 route-stop rows once per stop (50 stops = about 3.15 M iterations). It now builds the lookup once and resolves each stop in O(1): measured route scanning drops from 4.55 M to 63 K iterations, and with the cache warm each refresh costs 6 ms instead of 352 ms (about 58x)"
            ),
        ),

        Entry(
            version = "1.1.1",
            date = "2026-10-09",
            titleZhHant = "版本介面移植、班次表打直、路線公告",
            titleZhHans = "版本界面移植、班次表打直、路线公告",
            titleEn = "Version UI ported, vertical trip list, route notices",
            itemsZhHant = listOf(
                "版本膠囊版面全面跟足 code-to-app 原版 VersionPill：膠囊 padding 14/6、分隔符前後 6/6、掣距 8/10/10、三個掣尺寸 Sync 14 / History 15 / Copy 14、底色 surfaceContainerHigh @ 80%",
                "全面移植 code-to-app 嘅「App 內下載 + 安裝」功能：唔使再開瀏覽器，撳「下載」直接喺 App 內下載 APK，會校驗 SHA-256（CI 自動生成），下載完撳「安裝」開系統安裝器",
                "班次時間表改為打直顯示：一張卡一個站，由上到下就係行車次序；淨係顯示最快開出嗰班（第 1 班），站名會連埋站號（例如「海麗邨巴士總站 (SS667)」）",
                "「公告」分頁改為**路線公告**，唔再顯示 App 更新公告：港鐵系顯示真·官方服務提示；其餘營辦商因為官方 ETA 接口冇公告欄位，改為由官方即時數據推導出「全線冇班次／部分站冇班次／班次間隔偏大／路線運作正常」，並一律標明「由官方數據推導」",
                "修好搜尋頁鍵盤顯示唔到嘅問題：結果列表之前冇 weight，內容總高超出螢幕，鍵盤被推咗出畫面外；而家列表用 weight(1f) 食晒剩餘空間，鍵盤固定喺底，路線結果顯示喺鍵盤上面；字母圓鍵紫色亦加深咗，對比更清楚"
            ),
            itemsZhHans = listOf(
                "版本胶囊版面全面跟足 code-to-app 原版 VersionPill：胶囊 padding 14/6、分隔符前后 6/6、按钮距 8/10/10、三个按钮尺寸 Sync 14 / History 15 / Copy 14、底色 surfaceContainerHigh @ 80%",
                "全面移植 code-to-app 的「App 内下载 + 安装」功能：不用再开浏览器，点「下载」直接在 App 内下载 APK，会校验 SHA-256（CI 自动生成），下载完点「安装」开系统安装器",
                "班次时间表改为打直显示：一张卡一个站，由上到下就是行车次序；只显示最快开出那班（第 1 班），站名会连埋站号（例如「海丽邨巴士总站 (SS667)」）",
                "「公告」分页改为**路线公告**，不再显示 App 更新公告：港铁系显示真·官方服务提示；其余运营商因为官方 ETA 接口没公告字段，改为由官方即时数据推导出「全线没班次／部分站没班次／班次间隔偏大／路线运作正常」，并一律标明「由官方数据推导」",
                "修好搜索页键盘显示不到的问题：结果列表之前没有 weight，内容总高超出屏幕，键盘被推到画面外；现在列表用 weight(1f) 吃掉剩余空间，键盘固定在底，路线结果显示在键盘上面；字母圆键紫色也加深了，对比更清楚"
            ),
            itemsEn = listOf(
                "The version pill now matches the code-to-app VersionPill exactly: 14/6 pill padding, 6/6 around the separator, 8/10/10 icon gaps, Sync 14 / History 15 / Copy 14 dp, surfaceContainerHigh at 80%",
                "Ported code-to-app\u2019s in-app download and install: no browser needed \u2014 tap Download to fetch the APK inside the app, with a SHA-256 check (generated by CI), then tap Install to open the system installer",
                "The trip timetable is now vertical: one card per stop, top to bottom in travel order, showing only the next departure (trip 1), with stop codes in the name (e.g. \u201cHoi Lai Estate Bus Terminus (SS667)\u201d)",
                "The Notices tab is now **route notices** rather than app announcements: the MTR family shows genuine official service alerts; since the other operators\u2019 arrival APIs have no notice field, the app now derives route condition from live official data (no trips at all / some stops empty / large headway / running normally), always labelled \u201cDerived from official data\u201d",
                "Fixed the search keyboard not appearing: the results list had no weight, so the content overflowed and pushed the keyboard off-screen; the list now uses weight(1f), the keyboard stays pinned at the bottom and routes show above it. The round letter keys are also a deeper purple for better contrast"
            ),
        ),

        Entry(
            version = "1.1.0",
            date = "2026-10-09",
            titleZhHant = "路線頁加班次表同公告、搜尋頁改版",
            titleZhHans = "路线页加班次表同公告、搜索页改版",
            titleEn = "Route page gains trip timetable and notices; search page redesigned",
            itemsZhHant = listOf(
                "路線頁新增「班次」分頁：由官方即時到站預報構建班次表（第 N 班 = 各站預報嘅第 N 項），並推算班次間隔",
                "班次表最多顯示 6 班車，沿線超過 12 個站會自動均勻取樣，唔會打爆官方接口",
                "路線頁新增「公告」分頁：港鐵官方服務提示（改道／延誤／暫停），可撳「查看詳情」開連結",
                "增設「觀察到嘅營運時段」：官方冇公開頭／尾班車接口，所以靠日常開 App 慢慢累積，會標明係觀察值而唔係官方公佈",
                "搜尋頁版面改版：頂部加主色「搜尋」標題列、輸入框改白底「輸入路線號碼」",
                "搜尋結果改為「路線號 ｜ 往 XX ｜ 箭頭」一列式，撳一下即開沿途車站",
                "非九巴路線會顯示「目的地 - 營辦商」（例如「大澳 - 嶼巴」），下一行標示官方全名同「資料由 DATA.GOV.HK 提供」",
                "路線專用鍵盤改為藍灰色調，數字鍵、字母圓鍵對比更清楚"
            ),
            itemsZhHans = listOf(
                "路线页新增「班次」分页：由官方即时到站预报构建班次表（第 N 班 = 各站预报的第 N 项），并推算班次间隔",
                "班次表最多显示 6 班车，沿线超过 12 个站会自动均匀取样，不会打爆官方接口",
                "路线页新增「公告」分页：港铁官方服务提示（改道／延误／暂停），可点「查看详情」开链接",
                "增设「观察到的营运时段」：官方没有公开头／尾班车接口，所以靠日常开 App 慢慢累积，会标明是观察值而不是官方公布",
                "搜索页版面改版：顶部加主色「搜索」标题栏、输入框改白底「输入路线号码」",
                "搜索结果改为「路线号 ｜ 往 XX ｜ 箭头」一列式，点一下即开沿途车站",
                "非九巴路线会显示「目的地 - 运营商」（例如「大澳 - 屿巴」），下一行标示官方全名同「资料由 DATA.GOV.HK 提供」",
                "路线专用键盘改为蓝灰色调，数字键、字母圆键对比更清楚"
            ),
            itemsEn = listOf(
                "Route page gained a Trips tab: the timetable is built from the live official arrivals (trip N = the Nth prediction at each stop), with the headway derived from it",
                "Shows up to 6 trips; routes with more than 12 stops are sampled evenly so the official endpoints aren\u2019t hammered",
                "Route page gained a Notices tab with official MTR service alerts (diversion / delay / suspension) and a Details link",
                "Added an \u201cobserved service window\u201d: there is no public first/last-bus API, so it accumulates as you use the app and is labelled as observed rather than official",
                "Search page redesigned: a primary-coloured Search title bar and a white field reading \u201cEnter a route number\u201d",
                "Results are now one row each \u2014 route number, destination, chevron \u2014 and a tap opens the stop list",
                "Non-KMB routes show \u201cdestination - operator\u201d (e.g. \u201cTai O - NLB\u201d) with the official name and the DATA.GOV.HK credit underneath",
                "The route keypad is now blue-grey, with clearer contrast on the digit and round letter keys"
            )
        ),

        Entry(
            version = "1.0.5",
            date = "2026-10-09",
            titleZhHant = "改名 HKATE、版本膠囊去徽章、背景修復收緊",
            titleZhHans = "改名 HKATE、版本胶囊去徽章、背景修复收紧",
            titleEn = "Renamed to HKATE, cleaner version pill, tighter background repair",
            itemsZhHant = listOf(
                "應用程式改名為 HKATE（顯示名「HKATE 巴士到站預報」），關於頁同複製版本號都跟埋改",
                "版本膠囊跟足 code-to-app 原版：移除圓點徽章，版面變成「v1.0.5 · 6 + 三個掣」；有新版本時 versionCode 數字會轉紅，唔使靠徽章都睇得出",
                "底部狀態欄、設定頁、關於頁三處膠囊繼續共用同一個元件，改一處三處都改",
                "修好「背景補充緊城巴／小巴資料」提示永久掛住唔消失嘅問題：而家淨係「而家補緊」先顯示，補完即收",
                "更重要：修好城巴／小巴車站其實從來冇自動補過嘅 bug —— 以前淨係第一次裝 App（重建索引）先會補，第二次開 App 起索引由磁碟載入就直接跳過，所以附近車站永遠淨係得九巴",
                "背景修復改為靜默模式：唔彈訊息、唔掝忙碌狀態，唔會蓋住你自己開嘅動作",
                "補齊簡體中文（values-b+zh+Hans）缺咗嘅 76 個字串，四份語言檔而家完全一致"
            ),
            itemsZhHans = listOf(
                "应用程序改名为 HKATE（显示名「HKATE 巴士到站预报」），关于页同复制版本号都跟着改",
                "版本胶囊跟足 code-to-app 原版：移除圆点徽章，版面变成「v1.0.5 · 6 + 三个按钮」；有新版本时 versionCode 数字会转红，不用靠徽章都看得出",
                "底部状态栏、设置页、关于页三处胶囊继续共用同一个元件，改一处三处都改",
                "修好「背景补充紧城巴／小巴资料」提示永久挂着不消失的问题：现在只有「正在补充」才显示，补充完即收",
                "更重要：修好城巴／小巴车站其实从来没有自动补过的 bug —— 以前只有第一次装 App（重建索引）才会补，第二次开 App 起索引由磁盘载入就直接跳过，所以附近车站永远只有九巴",
                "背景修复改为静默模式：不弹消息、不设置忙碌状态，不会盖住你自己开的动作",
                "补齐简体中文（values-b+zh+Hans）缺了的 76 个字符串，四份语言文件现在完全一致"
            ),
            itemsEn = listOf(
                "The app is now called HKATE (display name \u201cHKATE bus ETA\u201d), including the About page and the copy-version text",
                "The version pill now matches code-to-app exactly: the dot badge is gone, so it reads \u201cv1.0.5 \u00b7 6\u201d plus three icons; when an update exists the version number turns red, so the badge isn\u2019t needed",
                "The bottom bar, Settings and About still share one pill component, so a change lands in all three at once",
                "Fixed the \u201cadding Citybus / GMB stops in the background\u201d note never going away: it now shows only while a repair is actually running",
                "More importantly, fixed a bug where Citybus / GMB stops were never actually added: it only ran on first install (when the index was rebuilt) and was skipped on every later launch, so nearby stops were KMB-only forever",
                "Background repair is now silent: no toast and no busy flag, so it won\u2019t interrupt whatever you started yourself",
                "Filled in 76 strings missing from the Simplified Chinese (values-b+zh+Hans) file - all four language files now match"
            )
        ),
        Entry(
            version = "1.0.4",
            date = "2026-10-09",
            titleZhHant = "嶼巴英文名、地圖離線包、輕鐵自動掃描",
            titleZhHans = "屿巴英文名、地图离线包、轻铁自动扫描",
            titleEn = "NLB English names, offline map pack, auto Light Rail scan",
            itemsZhHant = listOf(
                "嶼巴站名補返英文：官方接口冇 name_en，所以內置咗約 90 個大嶼山站名嘅官方英文對照（政府地名 + 嶼巴站牌），唔係機器翻譯；查唔到一律維持中文",
                "地圖離線包：睇過嘅路線會自動預取 zoom 12–16 嘅瓦片存落本地，WebView 攞瓦片時由本地攔截供給，飛機模式都出到真實地圖（唔再靠唔可靠嘅 WebView 快取）",
                "輕鐵特別班次自動掃描：開「鐵路」分頁會自動喺背景掃（快取 7 日，過期自動重掃），唔使撳掣都有；手動即時重掃嘅掣照樣保留",
                "主頁會提示「背景補充緊城巴／小巴資料」，唔會再無聲無息淨係出九巴",
                "開 App 全自動：索引、ETA 資料修復、定位搵附近路線三樣都唔使再自己撳 —— 一開 App 就自動做",
                "ETA 資料修復新增逐個營辦商修復：九巴／城巴／嶼巴／專線小巴／港鐵巴士／港鐵／輕鐵各有自己嘅修復掣，淨係換嗰個營辦商嘅資料，唔使成個索引重建",
                "自動體檢：索引冇嘢／過期（超過 14 日）會開 App 自動重建，唔使去設定頁撳「ETA 資料修復」",
                "定位權限統一喺開 App 嗰陣請求一次，主頁唔會再彈多次對話框",
                "「版本更新」區塊改為 code-to-app 嗰種 VersionPill 膠囊：撳膠囊檢查更新，右邊三個掣係檢查更新／版本歷史／複製版本號",
                "底部狀態欄、設定頁、關於頁三處嘅版本膠囊統一為同一個共用元件，樣式完全一致"
            ),
            itemsZhHans = listOf(
                "屿巴站名补回英文：官方接口没有 name_en，所以内置了约 90 个大屿山站名的官方英文对照（政府地名 + 屿巴站牌），不是机器翻译；查不到一律维持中文",
                "地图离线包：看过的路线会自动预取 zoom 12–16 的瓦片存到本地，WebView 取瓦片时由本地拦截供给，飞行模式都能出真实地图（不再靠不可靠的 WebView 缓存）",
                "轻铁特别班次自动扫描：开「铁路」分页会自动在后台扫（缓存 7 天，过期自动重扫），不用按键都有；手动即时重扫的按钮照样保留",
                "主页会提示「背景补充紧城巴／小巴资料」，不会再无声无息只有九巴",
                "开 App 全自动：索引、ETA 资料修复、定位找附近路线三样都不用再自己按 —— 一开 App 就自动做",
                "ETA 资料修复新增逐个运营商修复：九巴／城巴／屿巴／专线小巴／港铁巴士／港铁／轻铁各有自己的修复按钮，净系换那个运营商的资料，唔使成个索引重建",
                "自动体检：索引没东西／过期（超过 14 天）会开 App 自动重建，不用去设置页按「ETA 资料修复」",
                "定位权限统一在开 App 时请求一次，主页不会再弹多次对话框",
                "「版本更新」区块改为 code-to-app 那种 VersionPill 胶囊：按胶囊检查更新，右边三个按钮是检查更新／版本历史／复制版本号",
                "底部状态栏、设置页、关于页三处的版本胶囊统一为同一个共用组件，样式完全一致"
            ),
            itemsEn = listOf(
                "NLB stops now have English names: the API has no name_en, so ~90 Lantau stop names ship with their official English names (government gazetteer + NLB signage) \u2014 not machine-translated; anything unmatched stays Chinese",
                "Offline map pack: viewed routes prefetch zoom 12\u201316 tiles to disk and the WebView serves them locally, so real maps render even in airplane mode (no longer reliant on the unreliable WebView cache)",
                "Light Rail special trips scan automatically: opening the Rail tab scans in the background (cached 7 days, rescans when stale) \u2014 no button press needed; the manual rescan button remains",
                "Home now says when Citybus / GMB stops are being added in the background, instead of silently showing KMB only",
                "Fully automatic on launch: index, ETA data repair and nearby-route location all run on startup - no buttons to press",
                "ETA data repair now has a per-operator button for KMB / Citybus / NLB / GMB / MTR Bus / MTR / Light Rail - it swaps just that operator\u2019s data instead of rebuilding the whole index",
                "Auto health check: an empty or stale index (older than 14 days) is rebuilt on startup, so you never need Settings then ETA data repair",
                "Location permission is requested once at startup instead of the Home tab prompting repeatedly",
                "The version-update block is now a code-to-app style VersionPill: tap the pill to check for updates, and the three icons are check / version history / copy version",
                "The version pill in the bottom bar, Settings and About is now one shared component, so all three look identical"
            )
        ),
        Entry(
            version = "1.0.3",
            date = "2026-10-08",
            titleZhHant = "已知限制大清掃：真實地圖、鐵路英文名、自動修復、版面重做",
            titleZhHans = "已知限制大清扫：真实地图、铁路英文名、自动修复、版面重做",
            titleEn = "Known-limitations cleanup: real map, rail English names, auto-repair, redesigned nearby",
            itemsZhHant = listOf(
                "路線地圖改用真實地圖：Leaflet + CARTO 深色瓦片，有底圖有街道、可縮放撳站名，唔使 API key；離線自動退回示意圖",
                "睇過嘅地圖有快取，離線都出到圖（第一次睇某條路線先要上網）",
                "鐵路站名加咗官方英文名：重鐵 98 個 + 輕鐵 68 個全部齊，英文介面顯示官方站名（唔係自行翻譯）",
                "鐵路頁線名喺英文介面下顯示官方英文名（如 Tuen Ma Line）",
                "新增手動選點：唔想授權定位都可以自己揀區／交通樞紐（16 個預設），主頁照樣搵到附近車站",
                "輕鐵特別班次（9xx）：鐵路頁加咗「掃描特別班次」，掃各站預報動態搵返出嚟，唔使硬編碼；結果有快取，掃一次之後開頁即刻顯示",
                "城巴／小巴深度修復改咗分批並發 + 增量，而且索引一建好就自動喺背景補，唔使人手撳「修復城巴／修復小巴」",
                "Widget 支援全部營辦商：之前係收藏冇記低路線，而家會記錄，Widget 淨係查嗰一條（1 個請求）",
                "附近車站版面重做：一個站做小標題，下面每條班次一張卡（路線號膠囊 + 目的地 + 分鐘數）",
                "撳任何一張班次卡 → 直接打開嗰條路線嘅沿途車站，唔使再經站嘅預報頁",
                "冇班次（例如過咗尾班車）嘅路線照樣出卡，撳落去睇沿途車站",
                "支援 Android 17（API 37）：targetSdk 升到 37，面向最新系統",
                "支援範圍：Android 8 – 17（minSdk 26 / targetSdk 37），Android 8 到 17 全部裝到",
                "工具鏈升級：AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10（compileSdk 37 嘅硬門檻）",
                "大屏自適應：Android 17 喺平板上會自動轉為可調整大小，版面唔會爆",
                "嶼巴站名補返英文：官方接口冇 name_en，內置咗約 90 個大嶼山站名嘅官方英文對照（唔係機器翻譯），查唔到維持中文",
                "地圖離線包：睇過嘅路線自動預取瓦片存落本地，飛機模式都出到真實地圖（唔再靠 WebView 快取）",
                "輕鐵特別班次自動掃描：開鐵路頁自動喺背景掃（快取 7 日），唔使撳掣；照樣可手動即時重掃",
                "主頁會提示「背景補充緊城巴／小巴資料」，唔會再無聲淨係得九巴"
            ),
            itemsZhHans = listOf(
                "路线地图改用真实地图：Leaflet + CARTO 深色瓦片，有底图有街道、可缩放点站名，不需 API key；离线自动退回示意图",
                "看过的地图有缓存，离线也能出图（第一次看某条路线才需要上网）",
                "铁路站名加了官方英文名：重铁 98 个 + 轻铁 68 个全部齐，英文界面显示官方站名（不是自行翻译）",
                "铁路页线名在英文界面下显示官方英文名（如 Tuen Ma Line）",
                "新增手动选点：不想授权定位也能自己选区／交通枢纽（16 个预设），主页照样找得到附近车站",
                "轻铁特别班次（9xx）：铁路页加了「扫描特别班次」，扫各站预报动态找出来，不需硬编码；结果有缓存，扫一次之后开页立即显示",
                "城巴／小巴深度修复改成分批并发 + 增量，而且索引一建好就自动在背景补，不用手动点「修复城巴／修复小巴」",
                "Widget 支持全部运营商：之前是收藏没记录路线，现在会记录，Widget 只查那一条（1 个请求）",
                "附近车站版面重做：一个站做小标题，下面每条班次一张卡（路线号胶囊 + 目的地 + 分钟数）",
                "点任何一张班次卡 → 直接打开那条路线的沿途车站，不用再经站的预报页",
                "没有班次（例如过了末班车）的路线照样出卡，点进去看沿途车站",
                "支持 Android 17（API 37）：targetSdk 升到 37，面向最新系统",
                "支持范围：Android 8 – 17（minSdk 26 / targetSdk 37），Android 8 到 17 都能装",
                "工具链升级：AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10（compileSdk 37 的硬门槛）",
                "大屏自适应：Android 17 在平板上会自动转为可调整大小，版面不会爆",
                "屿巴站名补回英文：官方接口没有 name_en，内置了约 90 个大屿山站名的官方英文对照（不是机器翻译），查不到维持中文",
                "地图离线包：看过的路线自动预取瓦片存到本地，飞行模式都能出真实地图（不再靠 WebView 缓存）",
                "轻铁特别班次自动扫描：开铁路页自动在后台扫（缓存 7 天），不用按键；照样可手动即时重扫",
                "主页会提示「背景补充紧城巴／小巴资料」，不会再无声只有九巴"
            ),
            itemsEn = listOf(
                "Route map now shows a real map: Leaflet + CARTO dark tiles with basemap, streets, zoom and tappable stop names — no API key. Falls back to the schematic view offline",
                "Maps you've already viewed are cached, so they render offline (only the first view of a route needs the network)",
                "Official English station names added: all 98 heavy-rail and 68 Light Rail stops, shown in the English interface (official names, not translations)",
                "Rail line names show their official English names (e.g. Tuen Ma Line) in the English interface",
                "New manual location picker: if you'd rather not grant GPS, pick a district or hub (16 presets) and Home still finds nearby stops",
                "Light Rail special trips (9xx): the Rail tab can scan stop arrivals to discover them dynamically — no hardcoded list; results are cached so one scan is enough",
                "Citybus / GMB repair is now batched-concurrent and incremental, and also runs automatically in the background once the index is built — no manual \"Repair Citybus / Repair GMB\"",
                "Widget now supports every operator: bookmarks never stored a route before, now they do, so the widget queries just that one route (a single request)",
                "Nearby stops redesigned: each stop is a small header, with one card per departure (route badge + destination + minutes)",
                "Tap any departure card to open that route's stop list directly — no detour through the stop's arrivals page",
                "Routes with no departures (e.g. after last bus) still get a card so you can view the stop list",
                "Android 17 (API 37) support: targetSdk raised to 37, targeting the newest platform",
                "Support range: Android 8 – 17 (minSdk 26 / targetSdk 37) — installs on everything from Android 8 to 17",
                "Toolchain upgrade: AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10 — the hard minimum for compileSdk 37",
                "Large-screen adaptive: Android 17 makes the app resizable on tablets, and the layout holds up",
                "NLB stops now have English names: the API has no name_en, so ~90 Lantau stops ship with their official English names (not machine-translated); unmatched names stay Chinese",
                "Offline map pack: viewed routes prefetch their tiles locally, so real maps render even in airplane mode (no longer reliant on the WebView cache)",
                "Light Rail special trips scan automatically: opening the Rail tab scans in the background (cached 7 days) - no button needed, and you can still rescan manually",
                "Home now says when Citybus / GMB stops are being added in the background instead of silently showing KMB only"
            )
        ),
        Entry(
            version = "1.0.2",
            date = "2026-10-08",
            titleZhHant = "主頁自動定位 + 更新介面改版",
            titleZhHans = "主页自动定位 + 更新界面改版",
            titleEn = "Auto-locate on home + redesigned update UI",
            itemsZhHant = listOf(
                "主頁自動定位：一開波就搵附近路線，每 60 秒自動重整，唔使再撳掣",
                "自動定位狀態列：圓點顏色顯示「定位中／已定位／未授權／搵唔到」，撳圓點可開關",
                "更新介面改版：移植自 code-to-app，顯示「新版本 → 目前版本 → 大小」同可展開嘅 Release Notes",
                "版本歷史改用 BottomSheet：每個版本可撳開睇內容，目前版本會標「已安裝」，舊版本可獨立下載",
                "語義化版本比較：正式版永遠排喺預覽版之上（1.0.0 > 1.0.0-beta1），唔會再撞名",
                "Release Notes 中英自動切換：用 <!-- zh-CN --> 分隔，App 會按介面語言揀其中一半",
                "修好 17 個編譯錯誤（缺 LocatePhase / Box / size / width import）"
            ),
            itemsZhHans = listOf(
                "主页自动定位：一开就找附近路线，每 60 秒自动重整，不用再点按钮",
                "自动定位状态栏：圆点颜色显示「定位中／已定位／未授权／找不到」，点圆点可开关",
                "更新界面改版：移植自 code-to-app，显示「新版本 → 当前版本 → 大小」与可展开的 Release Notes",
                "版本历史改用 BottomSheet：每个版本可点开看内容，当前版本会标「已安装」，旧版本可独立下载",
                "语义化版本比较：正式版永远排在预览版之上（1.0.0 > 1.0.0-beta1），不会再撞名",
                "Release Notes 中英自动切换：用 <!-- zh-CN --> 分隔，App 会按界面语言选其中一半",
                "修好 17 个编译错误（缺 LocatePhase / Box / size / width import）"
            ),
            itemsEn = listOf(
                "Auto-locate on Home: finds nearby routes on launch and refreshes every 60s — no button needed",
                "Auto-locate status bar: dot colour shows Locating / Located / Not granted / Unavailable; tap the dot to toggle",
                "Update dialog redesigned, ported from code-to-app: shows New version → Current version → Size plus expandable release notes",
                "Version history moved to a bottom sheet: tap any release to expand its notes, current version tagged \"installed\", older builds downloadable individually",
                "Semantic version comparison: a stable release always ranks above any pre-release (1.0.0 > 1.0.0-beta1) — no more name collisions",
                "Bilingual release notes: split with <!-- zh-CN --> and the app picks the half matching the interface language",
                "Fixed 17 compile errors (missing LocatePhase / Box / size / width imports)"
            )
        ),
        Entry(
            version = "1.0.1",
            date = "2026-10-08",
            titleZhHant = "到站預報頁改版 + 站號顯示修正",
            titleZhHans = "到站预报页改版 + 站号显示修正",
            titleEn = "Arrivals page redesigned + stop-code fixes",
            itemsZhHant = listOf(
                "到站預報頁改為卡片式：站名大字、站號獨立一行、營辦商分行，路線號做成膠囊標籤",
                "修好站號位顯示 routeId：長度超過 12 位一律唔顯示，唔會再出現兩組括號同一串數字",
                "查詢層加保護：站號異常時自動改用索引入面同名同營辦商嘅站，避免查唔到班次",
                "主頁 / 收藏 / 車站搜尋統一用新嘅站名顯示，唔會重複括號",
                "修好版本歷史對話框喺簡體同英文介面下嘅顯示問題",
                "修好 Compose 編譯問題（titleWithCode 缺 @Composable 標註）"
            ),
            itemsZhHans = listOf(
                "到站预报页改为卡片式：站名大字、站号独立一行、营运商分行，路线号做成胶囊标签",
                "修好站号位显示 routeId：长度超过 12 位一律不显示，不会再出现两组括号同一串数字",
                "查询层加保护：站号异常时自动改用索引里同名同营运商的站，避免查不到班次",
                "主页 / 收藏 / 车站搜索统一用新的站名显示，不会重复括号",
                "修好版本历史对话框在简体与英文界面下的显示问题",
                "修好 Compose 编译问题（titleWithCode 缺 @Composable 标注）"
            ),
            itemsEn = listOf(
                "Arrivals page redesigned as a card: large stop name, stop code on its own line, operator on a separate line, route shown as a pill badge",
                "Fixed the stop-code slot showing a routeId: anything over 12 chars is hidden, so no more double parentheses and a string of digits",
                "Query-layer guard: when the stop code looks wrong, fall back to the same-named stop in the index so arrivals still resolve",
                "Home / Favourites / stop search now share one stop-name formatter — no repeated parentheses",
                "Fixed the version-history dialog under Simplified Chinese and English",
                "Fixed a Compose issue (titleWithCode was missing the @Composable annotation)"
            )
        ),
        Entry(
            version = "1.0.0",
            date = "2026-10-08",
            titleZhHant = "第一個正式版本：原生化完成 + 路線鍵盤",
            titleZhHans = "第一个正式版本：原生化完成 + 路线键盘",
            titleEn = "First stable release: fully native + route keypad",
            itemsZhHant = listOf(
                "路線專用鍵盤：數字 0–9 + 圓形字母鍵（A B C E K M N S X），唔使切換系統鍵盤版面",
                "索引診斷：統計頁講清楚邊個營辦商點解係 0（如城巴冇全量車站接口）",
                "小巴索引加後備：三個 region 都抓唔到會自動試官方全量路徑 /route",
                "底部狀態欄：版本 · 計數徽章 + 重新整理 / 版本歷史 / 檢查更新",
                "版本歷史：本地 changelog + GitHub Releases（有新資料就並列顯示）",
                "搜尋頁拆成「路線」/「車站」兩個獨立分頁，各自記住關鍵字",
                "「路線索引」改名為「ETA 資料修復」，加入「淨係清除快取」",
                "固定簽名（keystore/hketa.jks，有效期至 2056），唔會再出現「套件衝突」",
                "CI 只輸出一份正式版 APK"
            ),
            itemsZhHans = listOf(
                "路线专用键盘：数字 0–9 + 圆形字母键（A B C E K M N S X），不用切换系统键盘版面",
                "索引诊断：统计页讲清楚哪个营运商为什么是 0（如城巴没有全量车站接口）",
                "小巴索引加后备：三个 region 都抓不到会自动试官方全量路径 /route",
                "底部状态栏：版本 · 计数徽章 + 刷新 / 版本历史 / 检查更新",
                "版本历史：本地 changelog + GitHub Releases（有新资料就并列显示）",
                "搜索页拆成「路线」/「车站」两个独立分页，各自记住关键字",
                "「路线索引」改名为「ETA 数据修复」，加入「仅清除缓存」",
                "固定签名（keystore/hketa.jks，有效期至 2056），不会再出现「套件冲突」",
                "CI 只输出一份正式版 APK"
            ),
            itemsEn = listOf(
                "Route keypad: digits 0–9 + round letter keys (A B C E K M N S X) — no layout switching",
                "Index diagnostics: the stats page now explains why an operator shows 0 (e.g. Citybus has no bulk-stop API)",
                "GMB fallback: if all three regions return nothing, the full /route endpoint is tried automatically",
                "Bottom status bar: version · count badge + refresh / version history / check for updates",
                "Version history: local changelog + GitHub Releases (shown side by side when available)",
                "Search split into separate Routes / Stops tabs, each keeping its own query",
                "\"Route index\" renamed to \"ETA data repair\", plus a \"Clear cache only\" option",
                "Fixed signing key (keystore/hketa.jks, valid to 2056) — no more \"package conflicts\"",
                "CI now emits a single release APK"
            )
        ),
        Entry(
            version = "0.9.0",
            date = "2026-10-07",
            titleZhHant = "ETA 資料修復與介面重整",
            titleZhHans = "ETA 数据修复与界面重整",
            titleEn = "ETA data repair and UI overhaul",
            itemsZhHant = listOf(
                "修好城巴 / 小巴「暫時冇車站資料」：改用分批並發 + 站號兜底",
                "介面改為 App1933 風格：主頁顯示附近車站即時預報",
                "新增收藏車站，收藏頁並發刷新全部 ETA",
                "桌面小工具（Widget）：顯示收藏站下一班車",
                "路線地圖：沿途車站頁可顯示 Canvas 走線圖",
                "英文介面改用官方 name_en 站名；鐵路線補官方英文名",
                "新增 values-b+zh+Hans，zh-SG 等簡體語系唔會再跌落繁體"
            ),
            itemsZhHans = listOf(
                "修好城巴 / 小巴「暂时没有车站资料」：改用分批并发 + 站号兜底",
                "界面改为 App1933 风格：主页显示附近车站即时预报",
                "新增收藏车站，收藏页并发刷新全部 ETA",
                "桌面小工具（Widget）：显示收藏站下一班车",
                "路线地图：沿途车站页可显示 Canvas 走线图",
                "英文界面改用官方 name_en 站名；铁路线补官方英文名",
                "新增 values-b+zh+Hans，zh-SG 等简体语系不会再跌落繁体"
            ),
            itemsEn = listOf(
                "Fixed Citybus / GMB \"no stop data\": batched concurrency + stop-code fallback",
                "UI reworked in App1933 style: home shows live arrivals for nearby stops",
                "Bookmarked stops, with the Favourites tab refreshing all of them concurrently",
                "Home-screen widget showing the next departure at bookmarked stops",
                "Route map: Canvas-drawn polyline on the stop list screen",
                "English UI now uses official name_en stop names; rail lines got official English names",
                "Added values-b+zh+Hans so zh-SG and other Simplified locales no longer fall back to Traditional"
            )
        ),
        Entry(
            version = "0.8.0",
            date = "2026-10-06",
            titleZhHant = "加入港鐵重鐵、輕鐵與專線小巴",
            titleZhHans = "加入港铁重铁、轻铁与专线小巴",
            titleEn = "MTR heavy rail, Light Rail and GMB minibus",
            itemsZhHant = listOf(
                "港鐵重鐵 10 條線 98 個站（離線內置，含官方座標）",
                "輕鐵 11 條常規線 68 個站",
                "專線小巴深度索引，附近車站納入小巴站",
                "底欄改為四個分頁：主頁 / 收藏 / 搜尋 / 設定"
            ),
            itemsZhHans = listOf(
                "港铁重铁 10 条线 98 个站（离线内置，含官方坐标）",
                "轻铁 11 条常规线 68 个站",
                "专线小巴深度索引，附近车站纳入小巴站",
                "底栏改为四个分页：主页 / 收藏 / 搜索 / 设置"
            ),
            itemsEn = listOf(
                "MTR heavy rail: 10 lines, 98 stations (bundled offline, with official coordinates)",
                "Light Rail: 11 regular routes, 68 stops",
                "GMB deep indexing — minibus stops now appear in Nearby",
                "Bottom bar reworked into four tabs: Home / Favourites / Search / Settings"
            )
        ),
        Entry(
            version = "0.7.0",
            date = "2026-10-05",
            titleZhHant = "三語介面與版本更新檢查",
            titleZhHans = "三语界面与版本更新检查",
            titleEn = "Trilingual UI and update checker",
            itemsZhHant = listOf(
                "繁體中文 / 简体中文 / English 三語切換，跟隨系統",
                "設定頁加入 GitHub Releases 版本更新檢查",
                "到站預報備註（月台、經馬場、服務延誤）跟隨語言"
            ),
            itemsZhHans = listOf(
                "繁体中文 / 简体中文 / English 三语切换，跟随系统",
                "设置页加入 GitHub Releases 版本更新检查",
                "到站预报备注（月台、经马场、服务延误）跟随语言"
            ),
            itemsEn = listOf(
                "Traditional / Simplified Chinese / English switching, follows the system",
                "Settings gained a GitHub Releases update checker",
                "Arrival remarks (platform, via Racecourse, service delay) are localised"
            )
        ),
        Entry(
            version = "0.6.0",
            date = "2026-10-04",
            titleZhHant = "原生化重寫",
            titleZhHans = "原生化重写",
            titleEn = "Native rewrite",
            itemsZhHant = listOf(
                "由 WebToApp（WebView 外殼）完全改寫為 Kotlin + Jetpack Compose 原生 App",
                "直接對接九巴 / 城巴 / 嶼巴 / 專線小巴 / 港鐵巴士官方 API",
                "路線搜尋、沿途車站、到站預報、附近車站（GPS）",
                "換上專屬 App 圖示（Adaptive Icon）"
            ),
            itemsZhHans = listOf(
                "由 WebToApp（WebView 外壳）完全改写为 Kotlin + Jetpack Compose 原生 App",
                "直接对接九巴 / 城巴 / 屿巴 / 专线小巴 / 港铁巴士官方 API",
                "路线搜索、沿途车站、到站预报、附近车站（GPS）",
                "换上专属 App 图标（Adaptive Icon）"
            ),
            itemsEn = listOf(
                "Fully rewritten from the WebToApp WebView shell into a native Kotlin + Jetpack Compose app",
                "Talks directly to the KMB / Citybus / NLB / GMB / MTR Bus official APIs",
                "Route search, route stops, arrivals, nearby stops (GPS)",
                "New Adaptive Icon"
            )
        )
    )

    /** 語言代碼 → 解析後嘅單語列表 */
    fun forLang(lang: String): List<Resolved> {
        val zhHans = lang.equals(AppLocale.ZH_HANS, ignoreCase = true) ||
            lang.startsWith("zh", ignoreCase = true) && !lang.contains("TW", true) &&
            !lang.contains("HK", true) && !lang.contains("Hant", true)
        val en = lang.equals(AppLocale.EN, ignoreCase = true) ||
            lang.startsWith("en", ignoreCase = true)

        return entries.map { e ->
            when {
                en -> Resolved(e.version, e.date, e.titleEn, e.itemsEn)
                zhHans -> Resolved(e.version, e.date, e.titleZhHans, e.itemsZhHans)
                else -> Resolved(e.version, e.date, e.titleZhHant, e.itemsZhHant)
            }
        }
    }

    /**
     * 由 GitHub Release 轉成本地紀錄（用嚟顯示線上嗰一段）。
     * Release notes 本身係開發者寫嘅原文，所以三語欄位全部填同一份。
     */
    fun fromRelease(r: GitHubRelease): Resolved {
        val items = r.body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotBlank() }
            .take(12)
            .toList()
            .ifEmpty { listOf(r.body.take(200).ifBlank { "（冇更新說明）" }) }
        return Resolved(
            version = r.tag_name.removePrefix("v").removePrefix("V").ifBlank { r.name },
            date = r.published_at.take(10),
            title = r.name.ifBlank { r.tag_name },
            items = items
        )
    }
}
