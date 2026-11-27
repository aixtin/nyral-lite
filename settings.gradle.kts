pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Chasm 系列构件阿里云镜像缺失且不回落, 定向到 Maven Central (WASM 插件运行时)
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("io.github.charlietap.chasm") }
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}
rootProject.name = "NyralLite"
include(":app")
include(":testplugin")
