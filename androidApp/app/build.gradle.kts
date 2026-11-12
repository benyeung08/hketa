plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hketa.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hketa.app"
        // minSdk = 24 → Android 7.0 (Nougat) is the lowest supported version.
        // targetSdk = 34 → Android 14.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Required when minSdk < 24 was the old default; explicit is safer.
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Keep line numbers for crash reports on older devices.
            isShrinkResources = false
        }
    }

    compileOptions {
        // Java 17 is used by the build toolchain, but the *runtime* API level
        // is governed by minSdk. Desugaring is enabled so that API 24 devices
        // get back-ported java.time / java.util.stream support.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required for Android 7.0+ (API 24) runtime compatibility.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    // AndroidX core — 1.13.x still supports API 21+.
    implementation("androidx.core:core-ktx:1.13.1")

    // AppCompat 1.7.x supports API 19+ → safe for API 24.
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Components 1.12.x supports API 19+ → safe for API 24.
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout — supports API 14+.
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle — supports API 21+.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Coroutines.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Retrofit + Gson converter.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Desugaring: back-ports java.time APIs to API 24+ devices.
    // This is what allows newer Kotlin / Java features to run on Android 7.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
