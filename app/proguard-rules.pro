# R8 混淆规则（release 构建）
#
# 应用自身全部保留：插件契约以字符串名分发工具，动态 Dex 插件通过反射加载宿主契约类，
# 混淆会改类名/方法名导致插件与宿主对不上。第三方库的混淆仍可达到提高逆向门槛的目的。
-keep class io.github.aixtin.nyrallite.** { *; }

# 外部插件共用同一份契约包名，需保证反射可达
-keep class io.github.aixtin.nyrallite.contract.** { *; }
-keep class io.github.aixtin.nyrallite.kernel.** { *; }

# CommonMark / Markwon：解析器与扩展经反射构造，保留
-keep class org.commonmark.** { *; }
-keep class io.noties.markwon.** { *; }
-keep class io.noties.prism4j.** { *; }
-dontwarn org.commonmark.**
-dontwarn io.noties.markwon.**
-dontwarn io.noties.prism4j.**

# org.json：平台提供，仅告警静默
-dontwarn org.json.**

# Kotlin 标准库：外部插件不打包 stdlib、依赖宿主提供，
# 若被 R8 裁掉插件用到的类（如 kotlin.collections.SetsKt）会在加载时 NoClassDefFoundError。
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# 协程：插件常直接使用（withContext / launch 等），一并保留
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# QuickJS JNI 桥：native 侧按类名反射查找 JsEngine 并回调 dispatch，
# 混淆改名会导致运行时 No implementation found / dispatch 找不到
-keep class io.github.aixtin.nyrallite.plugins.js.JsEngine { *; }

# readability4j / jsoup：http_fetch 的 HTML 正文提取
-keep class net.dankito.readability4j.** { *; }
-dontwarn net.dankito.readability4j.**
-dontwarn org.jsoup.**
# slf4j 是 readability4j 的传递依赖，Android 上无绑定实现，运行时回落 NOP 日志
-dontwarn org.slf4j.**
