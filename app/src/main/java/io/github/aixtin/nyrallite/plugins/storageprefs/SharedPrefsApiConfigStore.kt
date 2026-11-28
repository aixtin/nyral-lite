package io.github.aixtin.nyrallite.plugins.storageprefs

import android.content.Context
import io.github.aixtin.nyrallite.contract.ApiConfig
import io.github.aixtin.nyrallite.contract.ApiConfigStore

/**
 * [ApiConfigStore] 的 SharedPreferences 实现。
 * 沿用旧版文件名/键名，升级后用户已填的端点与 Key 不丢。
 */
class SharedPrefsApiConfigStore(context: Context) : ApiConfigStore {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun load(): ApiConfig {
        val enc = sp.getString(K_KEY_ENC, null)
        if (enc != null) {
            // 密文解不开（如换机恢复备份后 Keystore 密钥不存在）：按未配置处理，等用户重填
            return config(KeyProtector.decrypt(enc) ?: "")
        }
        // 旧版明文键：读出后立即迁移成密文
        val legacy = sp.getString(K_KEY, null)
        if (!legacy.isNullOrBlank()) {
            val cfg = config(legacy)
            save(cfg)
            return cfg
        }
        return config("")
    }

    override fun save(config: ApiConfig) {
        val key = config.apiKey.trim()
        val editor = sp.edit()
            .putString(K_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(K_MODEL, config.model.trim())
            .putInt(K_CONTEXT, config.contextTokens)
            .putInt(K_TOOL_ROUNDS, config.toolRounds)
        val enc = if (key.isEmpty()) "" else KeyProtector.encrypt(key)
        if (enc != null) {
            editor.putString(K_KEY_ENC, enc).remove(K_KEY).apply()
        } else {
            // 加密不可用：退回明文（与旧行为一致），不让用户配置丢失
            editor.putString(K_KEY, key).apply()
        }
    }

    private fun config(apiKey: String) = ApiConfig(
        baseUrl = sp.getString(K_URL, DEF_URL) ?: DEF_URL,
        apiKey = apiKey,
        model = sp.getString(K_MODEL, DEF_MODEL) ?: DEF_MODEL,
        contextTokens = sp.getInt(K_CONTEXT, 0),
        toolRounds = sp.getInt(K_TOOL_ROUNDS, 0),
    )

    override fun isConfigured(): Boolean = load().apiKey.isNotBlank()

    internal companion object {
        const val NAME = "nyral_lite_prefs"
        const val K_URL = "base_url"
        const val K_KEY = "api_key"
        const val K_KEY_ENC = "api_key_enc"
        const val K_MODEL = "model"
        const val K_CONTEXT = "context_tokens"
        const val K_TOOL_ROUNDS = "tool_rounds"

        // 默认给一个通用兼容端点占位，首次使用需改成自己的服务地址与模型名
        const val DEF_URL = "https://api.example.com/v1"
        const val DEF_MODEL = "default"
    }
}
