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
    // compileSdk = 34（CI 穩定支援，本地開發可自行改為 38）
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hketa.app"
        // minSdk = 24 → Android 7.0 (Nougat)
        // targetSdk 跟隨 compileSdk
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        // 自動遞增 versionCode（每次建置 +1）
        // 讀取 git commit count 作為 versionCode
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
            // 使用上面定義的簽名配置
            signingConfig = signingConfigs.getByName("release")
            // 如果簽名不可用，退化為 debug 簽名（CI 中仍然可建置）
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

    // 關閉非必要變體以加速建置
    variantFilter {
        if (buildType.name == "debug" && !project.hasProperty("buildAll")) {
            // 保留 debug
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Retrofit + Gson converter
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
