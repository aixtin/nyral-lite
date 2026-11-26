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

# Kotlin 协程：内部反射与调试元数据
-dontwarn kotlinx.coroutines.**
