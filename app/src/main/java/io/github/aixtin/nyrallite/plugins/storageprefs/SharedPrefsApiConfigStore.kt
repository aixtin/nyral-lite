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

    override fun load(): ApiConfig = ApiConfig(
        baseUrl = sp.getString(K_URL, DEF_URL) ?: DEF_URL,
        apiKey = sp.getString(K_KEY, "") ?: "",
        model = sp.getString(K_MODEL, DEF_MODEL) ?: DEF_MODEL,
        contextTokens = sp.getInt(K_CONTEXT, 0),
    )

    override fun save(config: ApiConfig) {
        sp.edit()
            .putString(K_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(K_KEY, config.apiKey.trim())
            .putString(K_MODEL, config.model.trim())
            .putInt(K_CONTEXT, config.contextTokens)
            .apply()
    }

    override fun isConfigured(): Boolean = load().apiKey.isNotBlank()

    private companion object {
        const val NAME = "nyral_lite_prefs"
        const val K_URL = "base_url"
        const val K_KEY = "api_key"
        const val K_MODEL = "model"
        const val K_CONTEXT = "context_tokens"

        // 默认给一个通用兼容端点占位，首次使用需改成自己的服务地址与模型名
        const val DEF_URL = "https://api.example.com/v1"
        const val DEF_MODEL = "default"
    }
}
