package io.github.aixtin.nyrallite.plugins.dex

import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.util.zip.ZipFile

/** APK Manifest 读取与 DexClassLoader 资源释放。 */
internal object DexSupport {

    /** 读入口类：优先 APK 内 assets/plugin-entry.txt，兼容旧的 META-INF/MANIFEST.MF。 */
    fun readPluginEntry(apkFile: File): String? =
        readEntryText(apkFile)?.let(::parseEntry)

    /** 读插件声明的契约版本（Contract-Version: 键）；未声明时视为 1.0。 */
    fun readContractVersion(apkFile: File): String? =
        readEntryText(apkFile)?.let(::parseContractVersion) ?: DEFAULT_CONTRACT_VERSION

    private fun readEntryText(apkFile: File): String? {
        try {
            // 必须用 ZipFile（走 central directory）：apksigner 重签后会把 META-INF/MANIFEST.MF
            // 挪到 APK Signing Block 之后，ZipInputStream 顺序读到签名块就停了。
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                var legacy: java.util.zip.ZipEntry? = null
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name.equals(ASSETS_ENTRY, ignoreCase = true)) {
                        return zip.getInputStream(e).bufferedReader().readText()
                    }
                    if (e.name.equals(LEGACY_ENTRY, ignoreCase = true)) legacy = e
                }
                legacy?.let { return zip.getInputStream(it).bufferedReader().readText() }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readEntryText failed", t)
        }
        return null
    }

    private fun parseEntry(text: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith("Plugin-Entry:") }
        ?.substringAfter(':')?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun parseContractVersion(text: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith("Contract-Version:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()
        ?.takeIf { it.isNotBlank() }

    private const val ASSETS_ENTRY = "assets/plugin-entry.txt"
    private const val LEGACY_ENTRY = "META-INF/MANIFEST.MF"
    private const val DEFAULT_CONTRACT_VERSION = "1.0"

    /** 只调用 ClassLoader.close()；加载失败时用，避免连带清理未注册的 dex。 */
    fun safeClose(loader: DexClassLoader) {
        runCatching { loader.javaClass.getMethod("close").invoke(loader) }
    }

    /** 卸载时用：关闭 ClassLoader 并反射关闭 dex 元素，防止文件句柄泄漏。 */
    fun closeAll(loader: DexClassLoader) {
        safeClose(loader)
        runCatching {
            val plField = loader.javaClass.superclass?.getDeclaredField("pathList") ?: return
            plField.isAccessible = true
            val pathList = plField.get(loader) ?: return
            val dexFields = pathList.javaClass.getDeclaredField("dexElements")
            dexFields.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val dexElements = dexFields.get(pathList) as? Array<*> ?: return
            for (element in dexElements) {
                runCatching {
                    val fField = element!!.javaClass.getDeclaredField("dexFile")
                    fField.isAccessible = true
                    val df = fField.get(element) ?: return@runCatching
                    runCatching { df.javaClass.getMethod("close").invoke(df) }
                }
            }
        }
    }

    private const val TAG = "NyralDexLoader"
}
