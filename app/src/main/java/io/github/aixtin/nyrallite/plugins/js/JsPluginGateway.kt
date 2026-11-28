package io.github.aixtin.nyrallite.plugins.js

import org.json.JSONObject

/**
 * JS 中间档能力网关：脚本在裸 QuickJS 里跑，唯一宿主入口 nyral.invoke(cap, args)，
 * 每个能力在这里按策略执行并审计。deny 为默认：回调缺省即拒绝。
 *
 * M1 先接 http_fetch（复用 WASM 通道的 fetcher），其余能力随 M2 扩展；
 * 契约与 WasmPluginGateway 对齐：能力即插座，网关之外无路可走。
 */
class JsPluginGateway(
    private val httpFetcher: ((url: String) -> ByteArray?)? = null,
    private val onAudit: (entry: String) -> Unit = {},
) {
    val auditLog = mutableListOf<String>()

    private fun audit(entry: String) {
        auditLog += entry
        onAudit(entry)
    }

    /** 挂到引擎：脚本此后可以请求已声明的能力。 */
    fun install() {
        JsEngine.dispatcher = ::dispatch
    }

    fun uninstall() {
        JsEngine.dispatcher = null
    }

    /** 引擎回调：按能力名分发；返回 null 即拒绝，脚本侧拿到 null。 */
    fun dispatch(cap: String, argsJson: String): String? = when (cap) {
        "http_fetch" -> httpFetch(argsJson)
        else -> {
            audit("$cap DENIED (unknown capability)")
            null
        }
    }

    private fun httpFetch(argsJson: String): String? {
        val url = try {
            JSONObject(argsJson).optString("url")
        } catch (_: Exception) {
            null
        }
        if (url.isNullOrEmpty()) {
            audit("http_fetch: DENIED (bad args)")
            return null
        }
        val fetcher = httpFetcher ?: run {
            audit("http_fetch: $url DENIED (fetcher missing)")
            return null
        }
        val bytes = fetcher(url) ?: run {
            audit("http_fetch: $url DENIED (fetch failed)")
            return null
        }
        audit("http_fetch: $url ALLOWED (${bytes.size}B)")
        return bytes.decodeToString()
    }

    /** 跑一段脚本：独立 runtime，跑完即焚。返回 JSON 或 "ERROR: " 前缀；null 表示引擎异常。 */
    fun run(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        install()
        val handle = JsEngine.newRuntime(MEMORY_LIMIT, STACK_LIMIT, timeoutMs)
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
