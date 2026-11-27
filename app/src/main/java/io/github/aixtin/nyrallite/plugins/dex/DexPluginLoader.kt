package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import android.util.Log
import io.github.aixtin.nyrallite.kernel.Plugin
import java.io.File
import java.io.InputStream

/** 一次成功加载的结果：插件实例 + 稳定 id + 已落盘的 APK。 */
data class LoadedPlugin(
    val plugin: Plugin,
    val pluginId: String,
    val apkFile: File,
)

/**
 * 已落盘但尚未执行任何插件代码的 APK。
 * 用于「签名不受信任时先弹窗确认」：确认前只读包信息，不构造 DexClassLoader。
 */
data class StagedApk(
    val file: File,
    val sha256: String,
    val packageName: String?,
    val versionName: String?,
)

/**
 * Dex 插件加载器：签名白名单 + 导入流程；ClassLoader 池在 [DexPool]。
 *
 * 核心流程：
 *   1. [trustSelfSigned] — 宿主签名 + 用户已信任的第三方签名进白名单
 *   2. [stage] — 导入第一步：复制到 filesDir/plugins/ → 读签名与包信息，不执行代码
 *   3. [installStaged] — 导入第二步：验签 → 读 Plugin-Entry → 加载 → 按 id 归一化
 *   4. [loadFromFile] — 冷启动恢复：从 {pluginId}.apk 重新加载
 *   5. [unload] — Kernel.uninstall 先清 Service/Tool 注册，再释放 ClassLoader
 *
 * Manifest 约定：插件 APK 的 META-INF/MANIFEST.MF 必须有一行：
 *   Plugin-Entry: com.example.myplugin.MyPlugin
 */
class DexPluginLoader(
    private val appContext: Context,
    signerStore: TrustedSignerStore,
) {
    private val signatures = ApkSignatures(appContext, signerStore)
    private val pool = DexPool(appContext, signatures)

    fun trustSelfSigned(): DexPluginLoader {
        signatures.trustSelfSigned()
        return this
    }

    /** 签名（证书 SHA-256）是否在白名单内。 */
    fun isTrusted(sha256: String): Boolean = signatures.isTrusted(sha256)

    /** 用户确认信任某个第三方签名：立即生效并持久化。 */
    fun trust(sha256: String) = signatures.trust(sha256)

    /**
     * 导入第一步：落盘到临时文件并读取签名/包信息。
     * 此阶段不构造任何 ClassLoader，第三方代码不会被提前执行。
     */
    fun stage(apkStream: InputStream): StagedApk? {
        val pending = File(pool.pluginsDir, "pending-${System.currentTimeMillis()}.apk")
        try {
            pending.outputStream().use { apkStream.copyTo(it) }
        } catch (t: Throwable) {
            Log.e(TAG, "stage: copy APK failed", t)
            pending.delete()
            return null
        } finally {
            runCatching { apkStream.close() }
        }
        if (!pending.exists() || pending.length() < MIN_APK_SIZE) {
            pending.delete()
            return null
        }

        val sha = signatures.sha256OfApk(pending)
        if (sha == null) {
            Log.e(TAG, "stage: 无法解析 APK 签名")
            pending.delete()
            return null
        }
        val pi = runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(pending.absolutePath, 0)
        }.getOrNull()

        return StagedApk(
            file = pending,
            sha256 = sha,
            packageName = pi?.packageName,
            versionName = pi?.versionName,
        )
    }

    /** 导入第二步：签名已受信任后才会被调用。 */
    fun installStaged(staged: StagedApk): LoadedPlugin? {
        if (!signatures.isTrusted(staged.sha256)) {
            Log.e(TAG, "installStaged: 签名未受信任 ${staged.sha256}")
            staged.file.delete()
            return null
        }
        val first = pool.load(staged.file) ?: run {
            staged.file.delete()
            return null
        }

        val realId = first.plugin.manifest.id
        if (!DexPool.validId(realId)) {
            Log.e(TAG, "installStaged: 非法插件 id: $realId")
            pool.unload(first.pluginId, deleteApk = false)
            staged.file.delete()
            return null
        }
        if (realId == first.pluginId) return first

        // 声明的 id 与临时文件名不同：释放临时 loader，把 APK 归一到 {id}.apk 再重新加载。
        // 不直接 rename 已加载的 dex：DexClassLoader 按路径懒加载，改路径可能抛 FileNotFound。
        pool.unload(first.pluginId, deleteApk = false)
        return pool.commit(staged.file, realId)
    }

    /** 冷启动恢复：从已持久化的 {pluginId}.apk 重新加载。 */
    fun loadFromFile(pluginId: String): LoadedPlugin? {
        if (!DexPool.validId(pluginId)) return null
        val file = pool.apkFileOf(pluginId) ?: return null
        if (!file.exists()) return null
        val loaded = pool.load(file) ?: return null
        if (loaded.plugin.manifest.id != pluginId) {
            Log.w(TAG, "manifest id ${loaded.plugin.manifest.id} != file id $pluginId")
            pool.unload(pluginId, deleteApk = false)
            return null
        }
        return loaded
    }

    /**
     * 卸载插件并释放 ClassLoader。
     * @param deleteApk true=彻底移除（删 APK）；false=仅禁用，保留 APK 供下次启用
     */
    fun unload(pluginId: String, deleteApk: Boolean = true) = pool.unload(pluginId, deleteApk)

    fun isLoaded(pluginId: String): Boolean = pool.isLoaded(pluginId)

    fun loadedPluginIds(): List<String> = pool.loadedPluginIds()

    private companion object {
        const val TAG = "NyralDexLoader"
        const val MIN_APK_SIZE = 1024L
    }
}
