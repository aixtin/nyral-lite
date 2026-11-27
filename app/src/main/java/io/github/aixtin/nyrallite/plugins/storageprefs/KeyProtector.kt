package io.github.aixtin.nyrallite.plugins.storageprefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 的本机加密：AES-256-GCM，密钥生成在 AndroidKeyStore 内，不可导出。
 * 密文格式：base64(iv + ciphertext)。
 *
 * 注意：Keystore 密钥不进云备份，换机恢复备份后旧密文解不开，
 * 读取方按"未配置"处理，用户重新填写即可。
 */
object KeyProtector {

    private const val TAG = "KeyProtector"
    private const val KS = "AndroidKeyStore"
    private const val ALIAS = "nyral_lite_api_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /** 加密失败返回 null，调用方退回明文存储，不让用户配置丢失。 */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }.onFailure { Log.e(TAG, "encrypt failed", it) }.getOrNull()

    /** 解密失败（密文被篡改或 Keystore 密钥不存在）返回 null。 */
    fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        require(raw.size > IV_BYTES) { "ciphertext too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_BITS, raw, 0, IV_BYTES),
        )
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }.onFailure { Log.e(TAG, "decrypt failed", it) }.getOrNull()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }
}
