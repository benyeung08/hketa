plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 有 CI Secrets 才啟用正式簽名；否則 release 產出 unsigned APK。
val ciKeystore: String? = System.getenv("HKETA_KEYSTORE")

android {
    namespace = "com.hketa.hybrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hketa.hybrid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (!ciKeystore.isNullOrBlank()) {
            create("ci") {
                // CI 傳入相對路徑（如 keystore.jks），相對於本 android/ 專案目錄
                storeFile = file(ciKeystore)
                storePassword = System.getenv("HKETA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HKETA_KEY_ALIAS")
                keyPassword = System.getenv("HKETA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            if (!ciKeystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("ci")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
