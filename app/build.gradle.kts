plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "hk.eta.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "hk.eta.app"
        minSdk = 24
        targetSdk = 34
        // Bump these before tagging vX.Y.Z: the in-app updater compares
        // versionName with the GitHub Release tag_name.
        versionCode = 2
        versionName = "1.0.1"
    }

    /**
     * Signature consistency for in-app updates.
     *
     * A public, development-only keystore is committed at app/hketa-debug.keystore.
     * Every debug build (local + every CI runner) is therefore signed with the SAME
     * certificate, so an updated APK can always overwrite the installed one.
     *
     * For tagged releases the CI can inject a real keystore via Secrets into
     * app/hketa-release.keystore. If it is absent, release falls back to the same
     * dev key, keeping the certificate stable. NEVER ship this dev key on Play.
     */
    signingConfigs {
        create("hketaDev") {
            storeFile = file("hketa-debug.keystore")
            storePassword = "hketa-dev-2026"
            keyAlias = "hketa"
            keyPassword = "hketa-dev-2026"
        }
        create("hketaRelease") {
            val injected = file("hketa-release.keystore")
            storeFile = if (injected.exists()) injected else file("hketa-debug.keystore")
            storePassword = System.getenv("HKETA_KEYSTORE_PASSWORD") ?: "hketa-dev-2026"
            keyAlias = System.getenv("HKETA_KEY_ALIAS") ?: "hketa"
            keyPassword = System.getenv("HKETA_KEY_PASSWORD") ?: "hketa-dev-2026"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hketaDev")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hketaRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
