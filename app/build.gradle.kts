import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

// 正式签名材料不入库（keystore.properties 已在 .gitignore）。
// 缺失时回落到 debug 签名，保证仓库 clone 后仍可出包。
val keystoreProps = rootProject.file("keystore.properties")
val hasReleaseKey = keystoreProps.exists()

// prism4j 通过 api 传递老版本 org.jetbrains:annotations-java5(17.0.0),
// 与 kotlin 自带的 annotations 产生重复类 (运行时崩), 全局排除, 统一用新版。
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    namespace = "io.github.aixtin.nyrallite"
    compileSdk = 37
    ndkVersion = "27.1.12297006"

    // 关于页读取版本与构建信息
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "io.github.aixtin.nyrallite"
        minSdk = 31
        targetSdk = 36
        // 版本号从 git 取：versionName=最近标签（去 v 前缀），versionCode=提交总数；git 不可用时退回手写值
        val gitTag = runCatching {
            ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .directory(rootDir)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
                .removePrefix("v")
        }.getOrDefault("0.1.2")
        val gitCommitCount = runCatching {
            ProcessBuilder("git", "rev-list", "--count", "HEAD")
                .directory(rootDir)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
                .toInt()
        }.getOrDefault(4)
        versionCode = gitCommitCount
        versionName = gitTag
        ndk { abiFilters += listOf("arm64-v8a") }

        // 构建时注入 git 提交哈希与构建时间：排查"装的是哪个包"用，失败不挡构建
        val gitHash = runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootDir)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
                .ifEmpty { "unknown" }
        }.getOrDefault("unknown")
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\"",
        )
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                val p = Properties()
                keystoreProps.inputStream().use { p.load(it) }
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 开 R8 压缩 + 混淆 + 资源裁剪，减小体积并提高逆向门槛
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        // 接口默认实现编译成 JVM default 方法：旧插件未实现新成员也能继承，避免破坏性变更
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    // WASM 插件运行时: 纯 Kotlin 沙箱, 能力网关模式见 plugins/wasm (设计稿 2026-11-28)
    implementation("io.github.charlietap.chasm:chasm:2.5.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    // 唯一 Markdown 渲染引擎：流式前缀与收尾全走这一条路径
    // 排除 markwon 传递的旧版 commonmap(com.atlassian.commonmark:0.13, 类包名同为 org.commonmark),
    // 统一用新版 0.21(提供 sourceSpans 等 API); 表格由 MarkdownRenderer 自绘, 移除 ext-tables
    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "com.atlassian.commonmark")
    }
    testImplementation("junit:junit:4.13.2")
    // Android 自带 org.json 在本地单测里只是空壳，解析器测试使用 JVM 实现
    testImplementation("org.json:json:20240303")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2") {
        exclude(group = "com.atlassian.commonmark")
    }
    // 代码块语法高亮: 基于 prism4j。同样需排除 markwon 传递的旧版 commonmark (同包类冲突)。
    implementation("io.noties.markwon:syntax-highlight:4.6.2") {
        exclude(group = "com.atlassian.commonmark")
    }
    // prism4j 核心 (syntax-highlight 会传递, 显式声明以保证 @PrismBundle 注解在编译期可见):
    // 它把根 annotations 源码编进本 artifact, 且通过 api 传递 annotations-java5 (已全局排除)。
    implementation("io.noties:prism4j:2.0.0")
    // prism4j-bundler 注解处理器: 扫描 @PrismBundle, 生成 GrammarLocatorDef + 各语言 grammar 源码。
    kapt("io.noties:prism4j-bundler:2.0.0")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
