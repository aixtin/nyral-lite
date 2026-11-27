package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.content.pm.ApplicationInfo
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * APK 信任校验：信任对象是整个 APK 文件的 SHA-256，按插件 id 绑定。
 *
 * 证书指纹只回答「谁签的」，不回答「内容有没有被改过」——解析签名块拿证书
 * 并不校验签名与内容的对应关系，改过内容的 APK 可以原样保留旧证书。
 * 文件哈希才是完整性证明：任何字节改动都会改变哈希。代价是插件每次更新
 * 都要重新确认一次，这是有意为之。
 *
 * 信任记录按插件 id 绑定（更新覆盖写、卸载即删），回装旧版本会因哈希
 * 对不上重新弹窗。证书指纹仍会提取，但只用于弹窗展示与 debug 豁免判定。
 */
internal class ApkSignatures(
    private val appContext: Context,
    private val store: TrustedApkStore,
) {
    /** 已确认但尚未绑定插件 id 的哈希：导入确认后、装载完成前的过渡窗口用。 */
    private val pending = LinkedHashSet<String>()

    /** 宿主自身签名证书指纹：仅 debug 构建用于豁免同签名的测试插件。 */
    private val hostCertSha: String? = collectHostCert()

    /** 导入确认后的信任判定：哈希在 pending，或（仅 debug）与宿主同签名。 */
    fun isTrusted(fileSha256: String, certFingerprint: String?): Boolean =
        fileSha256 in pending || isDebugHostSigned(certFingerprint)

    /** 加载前的信任判定：插件 id 绑定的哈希匹配，或走 [isTrusted] 的过渡通道。 */
    fun isTrusted(pluginId: String, fileSha256: String, certFingerprint: String?): Boolean =
        store.get(pluginId) == fileSha256 || isTrusted(fileSha256, certFingerprint)

    /** 用户确认信任某个插件 APK：先记 pending，装载成功后由 [bind] 绑定 id 持久化。 */
    fun trust(fileSha256: String) {
        pending.add(fileSha256)
    }

    /** 装载成功：文件哈希绑定到插件 id（覆盖写，更新即换绑）。 */
    fun bind(pluginId: String, fileSha256: String) {
        store.put(pluginId, fileSha256)
        pending.remove(fileSha256)
    }

    /** 插件被彻底移除：信任记录一并删除，旧版本回装需重新确认。 */
    fun revoke(pluginId: String) {
        store.remove(pluginId)
    }

    /** 仅 debug 构建生效：与宿主同签名（同一 debug keystore）的测试插件免弹窗。 */
    private fun isDebugHostSigned(certFingerprint: String?): Boolean =
        isDebugBuild && certFingerprint != null && certFingerprint == hostCertSha

    private val isDebugBuild: Boolean =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun collectHostCert(): String? = runCatching {
        val pm = appContext.packageManager
        @Suppress("DEPRECATION")
        val pi = pm.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES,
        )
        pi.signingInfo?.apkContentsSigners?.firstOrNull()?.let { sha256(it.toByteArray()) }
            ?: pi.signatures?.firstOrNull()?.let { sha256(it.toByteArray()) }
    }.getOrNull()

    /** 整个 APK 文件的 SHA-256：信任判定与每次加载前的完整性校验都用它。 */
    fun fileSha256Of(apkFile: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { input ->
            val buf = ByteArray(BUF_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** 签名者证书的 SHA-256（v2/v3 优先，fallback v1）；只用于展示与 debug 豁免，不做信任判定。 */
    fun certFingerprintOf(apkFile: File): String? {
        try {
            @Suppress("DEPRECATION")
            val pi = appContext.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES,
            )
            pi?.signingInfo?.apkContentsSigners?.firstOrNull()?.let { sig ->
                return sha256(sig.toByteArray())
            }
            @Suppress("DEPRECATION")
            pi?.signatures?.firstOrNull()?.let { sig ->
                return sha256(sig.toByteArray())
            }
        } catch (_: Throwable) {
        }
        // Fallback：v1 签名（同样用 ZipFile，理由见 DexSupport.readPluginEntry）
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val n = e.name
                    if (!n.startsWith("META-INF/")) continue
                    if (!(n.endsWith(".RSA") || n.endsWith(".EC") || n.endsWith(".DSA"))) continue
                    val cf = CertificateFactory.getInstance("X.509")
                    val cert = zip.getInputStream(e).use { input ->
                        cf.generateCertificates(input).firstOrNull() as? X509Certificate
                    } ?: return null
                    return sha256(cert.encoded)
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun sha256(raw: ByteArray): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(raw).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private companion object {
        const val TAG = "NyralDexLoader"
        const val BUF_SIZE = 64 * 1024
    }
}
