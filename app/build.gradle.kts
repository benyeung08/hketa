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
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hketa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hketa.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
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
    }

    kotlinOptions {
        jvmTarget = "17"
    }

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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.google.android.gms:play-services-location:21.3.0")
}
