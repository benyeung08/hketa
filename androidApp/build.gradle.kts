import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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

    // Read API level from environment (set by CI), default to 36 for local builds
    val compileSdkVersion = System.getenv("COMPILE_SDK_VERSION")?.toIntOrNull() ?: 36

    compileSdk = compileSdkVersion

    defaultConfig {
        applicationId = "com.hketa.app"
        minSdk = 24
        targetSdk = compileSdkVersion
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        val gitCommitCount = try {
            val process = Runtime.getRuntime().exec("git rev-list --count HEAD")
            process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
        } catch (e: Exception) {
            1
        }
        versionCode = gitCommitCount
        println("Version Code: $versionCode")
    }

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
                println("Signing config loaded")
            } else {
                println("No signing key found, using debug key")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
