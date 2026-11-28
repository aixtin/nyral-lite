package io.github.aixtin.nyrallite.plugins.wasm

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * WASM/JS 插件的受限网络拉取：仅 https、5 秒超时、响应体超上限截断头部交付。
 * 明文 http 一律拒绝——插件请求的 URL 来自模型或插件作者，不放宽到明文。
 *
 * HTML 页面先提取正文再交付（见 [HtmlTextExtractor]），4KB 缓冲装的是干货；
 * 非 HTML（RSS XML/JSON/纯文本）原样返回。offset 可跳过响应体前 N 字节，
 * 用于超大响应的分段读取兜底。
 */
internal object WasmHttpFetcher {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /**
     * 提取正文前最多读取的原始字节数。HTML 的 head（样式/脚本）可轻松超过 4KB，
     * 若按 RESULT_CAP 截断后再提取，正文根本没进截断范围，提取只剩标题。
     */
    private const val MAX_PAGE_BYTES = 1_000_000

    fun fetch(url: String, offset: Int = 0): ByteArray? {
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
                input.use {
                    // 先读完整页面再提取正文，提取结果才截断到 RESULT_CAP——
                    // 模型侧工具结果本就按 4000 字截断，交付的是提取后的干货
                    val raw = readFrom(it, offset, MAX_PAGE_BYTES)
                    val text = HtmlTextExtractor.extract(raw.decodeToString())
                    when {
                        text != null ->
                            HtmlTextExtractor.truncateUtf8(text, WasmPluginGateway.RESULT_CAP)
                                .toByteArray(Charsets.UTF_8)
                        // 非 HTML（RSS XML/JSON/纯文本）：维持头部 4KB 截断交付
                        else -> raw.copyOf(minOf(raw.size, WasmPluginGateway.RESULT_CAP))
                    }
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** 跳过前 offset 字节（循环 read，skipNBytes 在部分 Android API 不可靠），最多再读 cap 字节 */
    private fun readFrom(input: InputStream, offset: Int, cap: Int): ByteArray {
        var skipped = 0
        val skipBuf = ByteArray(8192)
        while (skipped < offset) {
            val n = input.read(skipBuf, 0, minOf(skipBuf.size, offset - skipped))
            if (n < 0) break
            skipped += n
        }
        val out = ByteArrayOutputStream(minOf(cap, 8192))
        val buf = ByteArray(8192)
        var total = 0
        while (total < cap) {
            val n = input.read(buf, 0, minOf(buf.size, cap - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
