package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import io.github.aixtin.nyrallite.kernel.Plugin
import java.io.File

/**
 * 已加载插件的 ClassLoader 池：加载、按 id 归一化落盘与卸载。
 *
 * APK 放在 filesDir 而不是 codeCacheDir：后者会被系统在空间紧张时回收，
 * 导致已安装插件静默丢失。
 */
internal class DexPool(
    private val appContext: Context,
    private val signatures: ApkSignatures,
) {
    val pluginsDir: File
        get() = File(appContext.filesDir, PLUGINS_DIR).apply { mkdirs() }

    /** 插件 APK 的规范落盘路径；id 非法时返回 null，路径拼接统一收口在这里。 */
    fun apkFileOf(pluginId: String): File? =
        if (validId(pluginId)) File(pluginsDir, "$pluginId.apk") else null

    private val dexOptDir: File
        get() = File(appContext.codeCacheDir, DEX_OPT_DIR).apply { mkdirs() }

    private val trackers = LinkedHashMap<String, ClassLoaderTracker>()

    /** 实际加载：校验 → DexClassLoader → 反射实例化。pluginId 取文件名。 */
    fun load(apkFile: File): LoadedPlugin? {
        val pluginId = apkFile.nameWithoutExtension
        if (!apkFile.exists() || apkFile.length() < MIN_APK_SIZE) {
            Log.w(TAG, "load: file missing or too small: $pluginId")
            return null
        }

        // Android 13+ (targetSdk 33+) 禁止 DexClassLoader 加载可写文件，必须只读
        apkFile.setReadable(true, false)
        apkFile.setWritable(false, false)

        val entryClassName = DexSupport.readPluginEntry(apkFile)
        if (entryClassName.isNullOrBlank()) {
            Log.w(TAG, "load: Plugin-Entry not found in MANIFEST.MF")
            return null
        }

        // 每次加载前重验文件哈希：落盘后的 APK 被改动过就拒绝加载
        val sha = signatures.fileSha256Of(apkFile)
        if (sha == null) {
            Log.e(TAG, "load: 无法计算 APK 哈希")
            return null
        }
        if (!signatures.isTrusted(pluginId, sha, signatures.certFingerprintOf(apkFile))) {
            Log.e(TAG, "load: APK 哈希未受信任 $sha")
            return null
        }

        // 同名插件重复加载：先释放旧 loader，避免泄漏
        if (trackers.containsKey(pluginId)) unload(pluginId, deleteApk = false)

        val classLoader = try {
            DexClassLoader(
                apkFile.absolutePath,
                dexOptDir.absolutePath,
                null,
                appContext.classLoader,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "load: DexClassLoader create failed", t)
            return null
        }

        val plugin = try {
            val cls = classLoader.loadClass(entryClassName)
            val instance = cls.getDeclaredConstructor().newInstance()
            instance as? Plugin
        } catch (t: Throwable) {
            Log.e(TAG, "load: entry class instantiate failed", t)
            DexSupport.safeClose(classLoader)
            null
        }
        if (plugin == null) {
            Log.e(TAG, "load: entry class does not implement Plugin")
            return null
        }

        trackers[pluginId] = ClassLoaderTracker(classLoader, apkFile)
        return LoadedPlugin(plugin, pluginId, apkFile)
    }

    /** 把暂存 APK 归一到 {id}.apk 并重新加载；不直接改已加载 dex 的路径。 */
    fun commit(apkFile: File, pluginId: String): LoadedPlugin? {
        if (trackers.containsKey(pluginId)) unload(pluginId, deleteApk = true)
        val target = apkFileOf(pluginId) ?: run {
            Log.e(TAG, "commit: 非法插件 id: $pluginId")
            return null
        }
        val moved = runCatching {
            target.delete()
            apkFile.renameTo(target) || apkFile.copyTo(target, overwrite = true).let {
                apkFile.delete()
                true
            }
        }.getOrDefault(false)
        if (!moved || !target.exists()) {
            Log.e(TAG, "commit APK to $pluginId failed")
            apkFile.delete()
            target.delete()
            return null
        }
        return load(target)
    }

    /**
     * 卸载插件并释放 ClassLoader。
     * @param deleteApk true=彻底移除（删 APK）；false=仅禁用，保留 APK 供下次启用
     */
    fun unload(pluginId: String, deleteApk: Boolean) {
        val tracker = trackers.remove(pluginId)
        if (tracker != null) DexSupport.closeAll(tracker.loader)
        if (deleteApk) {
            signatures.revoke(pluginId)
            (tracker?.apkFile ?: apkFileOf(pluginId))?.delete()
        }
    }

    fun isLoaded(pluginId: String): Boolean = trackers.containsKey(pluginId)

    fun loadedPluginIds(): List<String> = trackers.keys.toList()

    private data class ClassLoaderTracker(val loader: DexClassLoader, val apkFile: File)

    companion object {
        private const val TAG = "NyralDexLoader"
        private const val PLUGINS_DIR = "plugins"
        private const val DEX_OPT_DIR = "dex_opt"
        private const val MIN_APK_SIZE = 1024L

        /** 插件 ID 白名单：防 manifest.id 拼路径越出插件目录。 */
        val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")

        fun validId(id: String): Boolean = ID_PATTERN.matches(id)
    }
}
