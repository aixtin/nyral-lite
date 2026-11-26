plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

// prism4j 通过 api 传递老版本 org.jetbrains:annotations-java5(17.0.0),
// 与 kotlin 自带的 annotations 产生重复类 (运行时崩), 全局排除, 统一用新版。
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    namespace = "io.github.aixtin.nyrallite"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aixtin.nyrallite"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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
