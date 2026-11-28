plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.aixtin.nyrallite.stressplugin"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aixtin.nyrallite.stressplugin"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
