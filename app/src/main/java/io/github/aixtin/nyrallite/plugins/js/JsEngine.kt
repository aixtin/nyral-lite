package io.github.aixtin.nyrallite.plugins.js

/**
 * QuickJS 引擎的 Kotlin 封装：裸机无 I/O，脚本侧唯一宿主入口是
 * `nyral.invoke(cap, argsJson)`，真实效果由 [dispatcher] 按策略提供，
 * 未设置时一律拒绝（返回 null）。与 WasmPluginGateway 同构：deny 为默认。
 *
 * 每个句柄独立 JSRuntime + JSContext（内存上限 / 栈上限 / 超时中断在 native 侧生效），
 * 用完必须 [close]。eval 返回 JSON 字符串；异常以 "ERROR: " 前缀回传。
 */
object JsEngine {
    init {
        System.loadLibrary("quickjs_bridge")
    }

    /** 能力分发：cap 为能力名，argsJson 为 JSON 参数；返回 JSON 字符串或 null（拒绝）。 */
    @Volatile
    var dispatcher: ((cap: String, argsJson: String) -> String?)? = null

    /** native 侧回调入口：保持静态签名，供 JNI 直接调用。 */
    @JvmStatic
    fun dispatch(cap: String, argsJson: String): String? = dispatcher?.invoke(cap, argsJson)

    @JvmStatic
    external fun newRuntime(memoryLimitBytes: Long, stackLimitBytes: Long, timeoutMs: Long): Long

    @JvmStatic
    external fun eval(handle: Long, code: String): String?

    @JvmStatic
    external fun close(handle: Long)
}
