import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ============================================================
//  簽名配置 — 長期一致性
//  優先從環境變數讀取（GitHub Actions）
//  其次從 keystore.properties 讀取（本地開發）
// ============================================================
val keystoreProperties = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProperties.load(it) }
}

fun getEnvOrProp(envKey: String, propKey: String): String? {
    return System.getenv(envKey) ?: keystoreProperties.getProperty(propKey)
}

android {
    namespace = "com.hketa.app"
    // compileSdk = 37（Android 17 - 最新穩定版）
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hketa.app"
        // minSdk = 24 → Android 7.0 (Nougat)
        // targetSdk 跟隨 compileSdk → API 37 (Android 17)
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        // 自動遞增 versionCode（每次建置 +1）
        val gitCommitCount = try {
            val process = Runtime.getRuntime().exec("git rev-list --count HEAD")
            process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
        } catch (e: Exception) {
            1
        }
        versionCode = gitCommitCount
        println("🔢 Version Code: $versionCode")
    }

    // --- 簽名配置 ---
    signingConfigs {
        create("release") {
            val storeFile = getEnvOrProp("SIGNING_KEYSTORE_FILE", "storeFile")
            val storePassword = getEnvOrProp("SIGNING_STORE_PASSWORD", "storePassword")
            val keyAlias = getEnvOrProp("SIGNING_KEY_ALIAS", "keyAlias")
            val keyPassword = getEnvOrProp("SIGNING_KEY_PASSWORD", "keyPassword")

            if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                println("✅ 簽名配置已載入（檔案: $storeFile）")
            } else {
                println("⚠️ 未找到簽名金鑰，Release 建置將使用 debug 簽名")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            if (getEnvOrProp("SIGNING_KEYSTORE_FILE", "storeFile") == null) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.19.1")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.8.0")

    // Material Components
    implementation("com.google.android.material:material:1.14.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.12.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Retrofit + Gson converter
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
