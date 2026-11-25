package io.github.aixtin.nyrallite.plugins.dex

import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.util.zip.ZipFile

/** APK Manifest 读取与 DexClassLoader 资源释放。 */
internal object DexSupport {

    /** 读 META-INF/MANIFEST.MF 的 Plugin-Entry 行。 */
    fun readPluginEntry(apkFile: File): String? {
        try {
            // 必须用 ZipFile（走 central directory）：apksigner 重签后会把 META-INF/MANIFEST.MF
            // 挪到 APK Signing Block 之后，ZipInputStream 顺序读到签名块就停了。
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!e.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) continue
                    val text = zip.getInputStream(e).bufferedReader().readText()
                    return text.lineSequence()
                        .firstOrNull { it.startsWith("Plugin-Entry:") }
                        ?.substringAfter(':')?.trim()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readPluginEntry failed", t)
        }
        return null
    }

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
