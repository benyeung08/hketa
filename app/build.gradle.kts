import java.util.Properties

// 可選：在 local.properties 提供自己的簽名檔
// signing.storeFile=../my.keystore
// signing.storePassword=***
// signing.keyAlias=***
// signing.keyPassword=***
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val customStoreFile = localProperties.getProperty("signing.storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }

val hasCustomSigning = customStoreFile?.isFile == true &&
    !localProperties.getProperty("signing.storePassword").isNullOrBlank() &&
    !localProperties.getProperty("signing.keyAlias").isNullOrBlank() &&
    !localProperties.getProperty("signing.keyPassword").isNullOrBlank()

plugins {
    id("com.android.application")
    // 唔好加 org.jetbrains.kotlin.android —— AGP 9 已經內置 Kotlin 編譯，
    // 加咗會報 "Cannot add extension with name 'kotlin'"
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hketa.app"
    // Android 17（Cinnamon Bun）＝ API 37 ＝ 支援範圍上線。
    // compileSdk 37 嘅硬門檻：AGP ≥ 9.1.1 + Gradle ≥ 9.3.1 + Build Tools 36.0.0
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hketa.app"
        // 26（Android 8.0 Oreo）= 支援範圍下線，對應「Android 8」。
        // 呢個亦係原本嘅設定：API 26 起系統自帶 java.time，
        // 唔使開 desugaring，亦避開 Play Services Location 嘅 minSdk 23 限制。
        minSdk = 26
        // 面向 Android 17：Google Play 由 2027 年 8 月起要求 target API 37
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 固定簽名：repo 內置 keystore/hketa.jks（鎖死唔變）。
    // 唔固定嘅話，CI 每次喺新 runner 上都會自動生成全新嘅 debug.keystore，
    // 令每份 APK 簽名都唔同 —— 用戶裝咗舊版再裝新版會彈
    // 「套件與現有的套件發生衝突，無法安裝」。
    val builtinStore = rootProject.file("keystore/hketa.jks")
    val hasBuiltinSigning = builtinStore.isFile

    signingConfigs {
        if (hasBuiltinSigning) {
            create("builtin") {
                storeFile = builtinStore
                storePassword = "hketa2026"
                keyAlias = "hketa"
                keyPassword = "hketa2026"
            }
        }
        if (hasCustomSigning) {
            create("custom") {
                storeFile = customStoreFile
                storePassword = localProperties.getProperty("signing.storePassword")
                keyAlias = localProperties.getProperty("signing.keyAlias")
                keyPassword = localProperties.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // debug 都用同一個固定 key，debug ⇄ release 互相可以覆蓋安裝
            if (hasBuiltinSigning) signingConfig = signingConfigs.getByName("builtin")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = when {
                hasCustomSigning -> signingConfigs.getByName("custom")
                hasBuiltinSigning -> signingConfigs.getByName("builtin")
                else -> signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 唔使開 coreLibraryDesugaring：minSdk 26（Android 8.0）起
        // 系統本身已經有 java.time（TimeUtil 用到嘅 OffsetDateTime / LocalTime），
        // 開咗反而白白加大 APK。
    }

    // 唔使再寫 kotlinOptions { jvmTarget } —— AGP 9 內置嘅 Kotlin 會自動跟
    // compileOptions 嘅 Java 版本對齊 jvmTarget。

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // 唔好把 keystore 打進 APK
            excludes += "/debug.keystore"
            excludes += "debug.keystore"
        }
    }
}

dependencies {
    // ⚠️ 關鍵：Compose Compiler 版本必須同 Kotlin 一致（AGP 9.1.1 內置 KGP 2.2.10，
    // 所以 compiler plugin 係 2.2.10）。而 compiler 2.2.x 要求嘅 Compose runtime
    // 係 1.9.x —— 即 BOM 要 2025.09 或以上。
    // 之前用 2024.10.01（runtime 1.7.x）會拋
    //   IncompatibleComposeRuntimeVersionException
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.google.android.gms:play-services-location:21.3.0")
}
