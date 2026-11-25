plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.aixtin.nyrallite.testplugin"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aixtin.nyrallite.testplugin"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
    }

    // 关键：debug 签名和主 app 完全相同（同一个 $HOME/.android/debug.keystore），
    // 这样 DexPluginLoader.trustSelfSigned() 会自动信任这个插件 APK。
    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}

// 入口类声明放在 src/main/assets/plugin-entry.txt：
// assets 在签名前打包，不会像「签名后 zip 追加 META-INF」那样破坏 v2/v3 签名。
