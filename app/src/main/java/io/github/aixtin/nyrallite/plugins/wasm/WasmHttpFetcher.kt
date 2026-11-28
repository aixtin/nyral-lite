package io.github.aixtin.nyrallite.plugins.wasm

import java.net.HttpURLConnection
import java.net.URL

/**
 * WASM 插件的受限网络拉取：仅 https、5 秒超时、响应体超上限即拒。
 * 明文 http 一律拒绝——插件请求的 URL 来自模型或插件作者，不放宽到明文。
 */
internal object WasmHttpFetcher {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    fun fetch(url: String): ByteArray? {
        if (!url.startsWith("https://")) return null
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val input = conn.inputStream ?: return@runCatching null
                // 多读一个字节探测超限，超限整体拒绝而不是交付半截响应
                val capped = input.use { it.readNBytes(WasmPluginGateway.RESULT_CAP + 1) }
                if (capped.size > WasmPluginGateway.RESULT_CAP) null else capped
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
