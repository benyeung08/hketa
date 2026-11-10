import java.io.File

plugins {
    // AGP 9 已內建 Kotlin 編譯器，套用 com.android.application 就會編譯 .kt，
    // 不需（也不可以）再套用 org.jetbrains.kotlin.android。
    id("com.android.application")
}

// ─────────────────────────────────────────────────────────────
// 永久簽名一致性
// keystore 與密碼絕不放入 repo，只由環境變數（GitHub Secrets）注入。
// 有提供 → debug / release / GitHub Release 全部用同一把 key；
// 沒提供 → 維持 AGP 預設（本機開發用），CI 會強制要求，見 workflow。
// ─────────────────────────────────────────────────────────────
val keystorePath = System.getenv("HKETA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystore = keystorePath?.let { File(it) }
val envStorePwd = System.getenv("HKETA_KEYSTORE_PASSWORD")
val envKeyAlias = System.getenv("HKETA_KEY_ALIAS")
val envKeyPwd = System.getenv("HKETA_KEY_PASSWORD")
val hasPermanentKey = keystore?.isFile == true &&
    !envStorePwd.isNullOrBlank() && !envKeyAlias.isNullOrBlank() && !envKeyPwd.isNullOrBlank()

// 憑證 SHA-256 指紋：CI 由 keystore 自動抽出，寫入 App 資源供啟動時自我校驗。
// 留空 = 未設定，校驗關閉（方便本機 debug build）。
val signingCertSha256 = (System.getenv("HKETA_SIGNING_CERT_SHA256") ?: "")
    .trim().lowercase().replace(":", "").replace(" ", "")

android {
    namespace = "hk.eta.app"
    compileSdk = 37          // Android 17 / API 37

    defaultConfig {
        applicationId = "hk.eta.app"
        minSdk = 26          // Android 8.0 Oreo — 支援下限
        targetSdk = 37       // Android 17 Cinnamon Bun — 已適配上限
        // 若要硬性禁止 Android 18+ 安裝，取消下行註解；
        // 一般建議保持註解，讓日後新系統仍可安裝（只是未實測）。
        // maxSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // 由 Gradle 產生，不在 res/values/strings.xml 手寫，避免重複資源。
        resValue("string", "expected_signing_cert_sha256", signingCertSha256)
    }

    // 同一個 signingConfig 同時綁到 debug 與 release，
    // 使用者先裝 debug 再裝 release 也不會出現 UPDATE_INCONSISTENT_CERTIFICATES。
    val permanentConfig = if (hasPermanentKey) {
        signingConfigs.create("permanent") {
            storeFile = keystore
            storePassword = envStorePwd
            keyAlias = envKeyAlias
            keyPassword = envKeyPwd
        }
    } else null

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (permanentConfig != null) signingConfig = permanentConfig
        }
        release {
            isMinifyEnabled = false
            if (permanentConfig != null) signingConfig = permanentConfig
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 內建 Kotlin 的 jvmTarget 會跟隨上面的 targetCompatibility，
    // 所以 AGP 9 不需要 kotlinOptions {}。
}

// 刻意不使用 AndroidX / Compose / 第三方 JSON 函式庫：
// 全部資料由 assets/hketa.json 以 Android 內建 org.json 解析。
// Kotlin stdlib 由 AGP 9 內建 Kotlin 自動提供，dependencies 為空，
// GitHub Actions 不會因下載第三方庫而失敗。
