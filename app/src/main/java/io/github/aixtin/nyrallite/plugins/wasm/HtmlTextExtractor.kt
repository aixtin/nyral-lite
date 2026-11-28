package io.github.aixtin.nyrallite.plugins.wasm

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

/**
 * HTML → 纯文本提取：让 4KB 结果缓冲装满正文干货而不是标签和脚本。
 *
 * 主力走 Readability4J（正文密度算法，剥导航/广告/脚本）；提取失败或正文为空时
 * 回落到 jsoup 全文 text()（仍剥标签，但保留全页文字）。非 HTML 输入返回 null，
 * 由调用方原样透传（RSS XML/JSON/纯文本不做处理）。
 * 纯函数无副作用，便于 fixture 单测；不做任何网络请求。
 */
internal object HtmlTextExtractor {

    /** 提取正文；输入不是 HTML 或提取结果为空时返回 null */
    fun extract(html: String): String? {
        if (!looksLikeHtml(html)) return null
        val article = runCatching { Readability4J("", html).parse() }.getOrNull()
        val text = article?.textContent?.trim()
        if (!text.isNullOrEmpty()) return normalize(text)
        // 回落：readability 认不出正文结构时，至少把标签剥干净
        val fallback = runCatching { Jsoup.parse(html).body().text() }.getOrNull()
        return fallback?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalize)
    }

    /** 按 UTF-8 字节截断，避免拆散多字节字符后产生乱码 */
    fun truncateUtf8(text: String, maxBytes: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        // 尾部可能落在多字节字符中间，lenient 解码会把残缺序列替换掉
        return String(bytes, 0, maxBytes, Charsets.UTF_8).trimEnd('�')
    }

    /** 嗅探：头部出现 doctype/html 标签即视为 HTML（content-type 不可靠，不依赖它） */
    private fun looksLikeHtml(html: String): Boolean {
        val head = html.substring(0, minOf(html.length, 2048)).lowercase().trimStart()
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<html")
    }

    /** 压缩连续空行，正文段落间最多留一个空行 */
    private fun normalize(text: String): String =
        text.replace(Regex("\\n{3,}"), "\n\n").trim()
}
