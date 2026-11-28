package io.github.aixtin.nyrallite.plugins.js

import org.json.JSONObject

/**
 * JS 中间档能力网关：脚本在裸 QuickJS 里跑，唯一宿主入口 nyral.invoke(cap, args)，
 * 每个能力在这里按策略执行并审计。deny 为默认：
 * 未声明的能力、回调缺省的能力，一律拒绝并审计。
 *
 * 能力装配只认声明清单（capabilities）：确认弹窗展示什么，运行时就有什么。
 * M2 先接 http_fetch（复用 WASM 通道的 fetcher），其余能力随批次扩展。
 */
class JsPluginGateway(
    private val capabilities: Set<String> = emptySet(),
    private val httpFetcher: ((url: String, offset: Int) -> ByteArray?)? = null,
    private val onAudit: (entry: String) -> Unit = {},
) : JsDispatcher {
    val auditLog = mutableListOf<String>()

    private fun audit(entry: String) {
        auditLog += entry
        onAudit(entry)
    }

    /** 引擎回调：按能力名分发；返回 null 即拒绝，脚本侧拿到 null。 */
    override fun dispatch(cap: String, argsJson: String): String? {
        if (cap !in capabilities) {
            audit("$cap DENIED (not declared)")
            return null
        }
        return when (cap) {
            "http_fetch" -> httpFetch(argsJson)
            else -> {
                audit("$cap DENIED (unknown capability)")
                null
            }
        }
    }

    private fun httpFetch(argsJson: String): String? {
        val parsed = try {
            JSONObject(argsJson)
        } catch (_: Exception) {
            null
        }
        val url = parsed?.optString("url")
        val offset = parsed?.optInt("offset", 0)?.takeIf { it > 0 } ?: 0
        if (url.isNullOrEmpty()) {
            audit("http_fetch: DENIED (bad args)")
            return null
        }
        val fetcher = httpFetcher ?: run {
            audit("http_fetch: $url DENIED (fetcher missing)")
            return null
        }
        val bytes = fetcher(url, offset) ?: run {
            audit("http_fetch: $url DENIED (fetch failed)")
            return null
        }
        audit("http_fetch: $url ALLOWED (${bytes.size}B, offset=$offset)")
        return bytes.decodeToString()
    }

    /** 跑一段脚本：独立 runtime，跑完即焚。返回 JSON 或 "ERROR: " 前缀；null 表示引擎异常。 */
    fun run(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val handle = JsEngine.newRuntime(MEMORY_LIMIT, STACK_LIMIT, timeoutMs, this)
        try {
            return JsEngine.eval(handle, script)
        } finally {
            JsEngine.close(handle)
        }
    }

    private companion object {
        const val MEMORY_LIMIT = 32L * 1024 * 1024
        const val STACK_LIMIT = 1024L * 1024
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
