import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    // AGP 9 已內置 Kotlin 編譯，唔好再加 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ── 簽名設定一律由外部提供 ───────────────────────────────
// 本機：local.properties（已被 .gitignore 忽略）
// CI  ：環境變數 / GitHub Secrets
// repo 入面**唔可以**再有任何 .jks / .b64 / 明文密碼。
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProp(key: String, env: String): String? =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val cfgStoreFile = signingProp("signing.storeFile", "HKETA_KEYSTORE")
    ?.let { rootProject.file(it) }
val cfgStorePassword = signingProp("signing.storePassword", "HKETA_STORE_PASSWORD")
val cfgKeyAlias = signingProp("signing.keyAlias", "HKETA_KEY_ALIAS")
val cfgKeyPassword = signingProp("signing.keyPassword", "HKETA_KEY_PASSWORD")

val hasCustomSigning = cfgStoreFile?.isFile == true &&
    cfgStorePassword != null && cfgKeyAlias != null && cfgKeyPassword != null

android {
    namespace = "com.hketa.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hketa.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 17
        versionName = "1.2.21"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 更新檢查嘅 repo 座標：fork 之後改呢兩行就得，唔使改 Kotlin
        buildConfigField("String", "UPDATE_OWNER", "\"benyeung08\"")
        buildConfigField("String", "UPDATE_REPO", "\"hketa\"")
    }

    signingConfigs {
        if (hasCustomSigning) {
            create("releaseKey") {
                storeFile = requireNotNull(cfgStoreFile)
                storePassword = requireNotNull(cfgStorePassword)
                keyAlias = requireNotNull(cfgKeyAlias)
                keyPassword = requireNotNull(cfgKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // 刻意唔指定 signingConfig：用 AGP 自動產生嘅 debug key。
            // debug ⇄ release 唔好共用同一條 key。
        }
        release {
            // 上線前可改 true + isShrinkResources，並補 Compose / serialization / JNI keep rules
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 冇外部 key 時仍要畀個 configuration 值（否則 sync / assembleDebug 會紅）；
            // 真正嘅 fail-fast 放喺下面 task 層，只有出 release 包先攔。
            signingConfig = if (hasCustomSigning) {
                signingConfigs.getByName("releaseKey")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 26 起系統已有 java.time，唔使開 desugaring
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose Compiler（AGP 9.1.1 內置 KGP 2.2.10）要求 runtime 1.9.x → BOM 2025.09+
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

    testImplementation("junit:junit:4.13.2")
}

// ── Release 簽名 fail-fast ───────────────────────────────
// 唔可以喺 release{} 入面直接 throw：Gradle configuration 階段會執行到，
// 連 assembleDebug / testDebugUnitTest / IDE Sync 都被拖紅。
// 放喺 task 嘅 doFirst，只有真正出 release 包先檢查。
tasks.matching {
    it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease")
}.configureEach {
    doFirst {
        if (!hasCustomSigning) {
            throw GradleException(
                "Release 簽名未設定：請喺 local.properties 提供 signing.storeFile / " +
                    "signing.storePassword / signing.keyAlias / signing.keyPassword，" +
                    "或設 HKETA_KEYSTORE / HKETA_STORE_PASSWORD / HKETA_KEY_ALIAS / " +
                    "HKETA_KEY_PASSWORD 環境變數。拒絕用 debug key 出 release 包。"
            )
        }
    }
}
