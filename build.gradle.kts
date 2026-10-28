plugins {
    // Android 17（API 37）要求最低 AGP 9.1.1（官方 API 級別對應表），
    // 低過呢個版本會認唔到 compileSdk 37。
    id("com.android.application") version "9.1.1" apply false

    // 重要：AGP 9.0 起 Kotlin 編譯已經內置落 AGP，
    // 千祈唔好再加 id("org.jetbrains.kotlin.android") —— 加咗會報
    // "Cannot add extension with name 'kotlin'"。
    // Kotlin 版本由 AGP 決定：AGP 9.1.1 內置 KGP 2.2.10。
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
