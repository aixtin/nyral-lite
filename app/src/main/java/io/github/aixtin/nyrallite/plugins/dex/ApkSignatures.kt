package io.github.aixtin.nyrallite.plugins.dex

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/** APK 签名校验：宿主自身签名 + 用户已信任的第三方签名。 */
internal class ApkSignatures(
    private val appContext: Context,
    private val signerStore: TrustedSignerStore,
) {
    private val trustedCerts = LinkedHashSet<String>()

    /** 把宿主自身签名 + 用户已信任的第三方签名加入白名单。 */
    fun trustSelfSigned() {
        val pm = appContext.packageManager
        val pi = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "trustSelfSigned getPackageInfo failed", t)
            null
        } ?: run {
            Log.w(TAG, "trustSelfSigned: getPackageInfo returned null")
            return
        }
        pi.signingInfo?.apkContentsSigners?.forEach { sha256(it.toByteArray())?.let(trustedCerts::add) }
        @Suppress("DEPRECATION")
        pi.signatures?.forEach { sha256(it.toByteArray())?.let(trustedCerts::add) }
        // 用户此前明确信任过的第三方签名
        trustedCerts.addAll(signerStore.all())
        if (trustedCerts.isEmpty()) Log.w(TAG, "trustSelfSigned: no certs collected")
    }

    /** 签名（证书 SHA-256）是否在白名单内。 */
    fun isTrusted(sha256: String): Boolean = sha256 in trustedCerts

    /** 用户确认信任某个第三方签名：立即生效并持久化。 */
    fun trust(sha256: String) {
        trustedCerts.add(sha256)
        signerStore.add(sha256)
    }

    /** 优先用 Android API 解析 APK Signature Block（v2/v3，API 24+）；fallback v1 签名文件。 */
    fun sha256OfApk(apkFile: File): String? {
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
                    val md = MessageDigest.getInstance("SHA-256")
                    return md.digest(cert.encoded).joinToString("") { "%02x".format(it) }
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
    }
}
