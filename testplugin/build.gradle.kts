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

// 构建后往 APK 里注入 META-INF/MANIFEST.MF（Android 默认构建不含这个）
// zip -r 往现有 APK ZIP 里追加 META-INF/ 目录。
// 现代 APK 用 v2/v3 签名（APK Signature Block 在 ZIP EOCD 之前），
// 追加文件不破坏签名校验 — DexPluginLoader 用 getPackageArchiveInfo 解析 v2/v3 签名。
tasks.register("injectPluginManifest") {
    dependsOn("assembleDebug")
    doLast {
        val apk = file("$buildDir/outputs/apk/debug/testplugin-debug.apk")
        if (!apk.exists()) {
            println("⚠️  APK 不存在，跳过注入"); return@doLast
        }
        val tmp = file("$buildDir/plugin_meta_tmp").apply { deleteRecursively(); mkdirs() }
        file("$tmp/META-INF").mkdirs()
        file("$tmp/META-INF/MANIFEST.MF").writeText(
            "Plugin-Entry: io.github.aixtin.nyrallite.testplugin.HelloPlugin\n",
        )
        exec { commandLine("zip", "-r", apk.absolutePath, "META-INF/"); workingDir = tmp }
        tmp.deleteRecursively()
        println("✅ 已注入 Plugin-Entry: io.github.aixtin.nyrallite.testplugin.HelloPlugin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
