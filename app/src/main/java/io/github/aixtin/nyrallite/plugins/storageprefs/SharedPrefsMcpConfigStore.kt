package io.github.aixtin.nyrallite.plugins.storageprefs

import android.content.Context
import io.github.aixtin.nyrallite.contract.McpConfig
import io.github.aixtin.nyrallite.contract.McpConfigStore

/**
 * [McpConfigStore] 的 SharedPreferences 实现。
 * 与 API 配置放同一个私有文件，改动只需一个地方备份。
 */
class SharedPrefsMcpConfigStore(context: Context) : McpConfigStore {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun load(): McpConfig = McpConfig(
        serverUrl = sp.getString(K_URL, "") ?: "",
        headerName = sp.getString(K_HEADER_NAME, "") ?: "",
        headerValue = sp.getString(K_HEADER_VALUE, "") ?: "",
    )

    override fun save(config: McpConfig) {
        sp.edit()
            .putString(K_URL, config.serverUrl.trim())
            .putString(K_HEADER_NAME, config.headerName.trim())
            .putString(K_HEADER_VALUE, config.headerValue.trim())
            .apply()
    }

    private companion object {
        const val NAME = "nyral_lite_prefs"
        const val K_URL = "mcp_server_url"
        const val K_HEADER_NAME = "mcp_header_name"
        const val K_HEADER_VALUE = "mcp_header_value"
    }
}
