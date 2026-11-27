package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * APK 信任校验：信任对象是整个 APK 文件的 SHA-256，而不是签名者证书。
 *
 * 证书指纹只回答「谁签的」，不回答「内容有没有被改过」——解析签名块拿证书
 * 并不校验签名与内容的对应关系，改过内容的 APK 可以原样保留旧证书。
 * 文件哈希才是完整性证明：任何字节改动都会改变哈希。代价是插件每次更新
 * 都要重新确认一次，这是有意为之。
 *
 * 证书指纹仍会提取，但只用于信任弹窗里给用户人工核对来源。
 */
internal class ApkSignatures(
    private val appContext: Context,
    private val store: TrustedApkStore,
) {
    private val trustedHashes = LinkedHashSet<String>()

    /** APK 文件整体 SHA-256 是否在信任名单内。 */
    fun isTrusted(fileSha256: String): Boolean = fileSha256 in trustedHashes

    /** 用户确认信任某个插件 APK：立即生效并持久化。 */
    fun trust(fileSha256: String) {
        trustedHashes.add(fileSha256)
        store.add(fileSha256)
    }

    /** 启动时把持久化的信任记录装回内存。 */
    fun restore() {
        trustedHashes.addAll(store.all())
        if (trustedHashes.isEmpty()) Log.i(TAG, "restore: 尚无受信任的插件 APK")
    }

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

    /** 签名者证书的 SHA-256（v2/v3 优先，fallback v1）；只用于弹窗展示，不做信任判定。 */
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
