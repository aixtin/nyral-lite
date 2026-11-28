package io.github.aixtin.nyrallite.plugins.wasm

import java.net.HttpURLConnection
import java.net.URL

/**
 * WASM 插件的受限网络拉取：仅 https、5 秒超时、响应体超上限截断头部交付。
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
            // 跟随同协议跳转（如末尾斜杠 301）；跨到明文 http 由 HttpURLConnection 自身拒绝
            conn.instanceFollowRedirects = true
            // 部分站点拒绝默认 UA，带一个常规标识
            conn.setRequestProperty("User-Agent", "NyralLite/0.1 (+https://github.com/aixtin/nyral-lite)")
            conn.setRequestProperty("Accept", "*/*")
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val input = conn.inputStream ?: return@runCatching null
                // 超上限截断头部交付而不是整体拒绝：模型侧工具结果本就按 4000 字截断，
                // 真实网页远超 4KB，整页拒收会让抓取对真实站点永远失败
                input.use { it.readNBytes(WasmPluginGateway.RESULT_CAP) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
