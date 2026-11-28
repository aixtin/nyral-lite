package io.github.aixtin.nyrallite.plugins.wasm

import io.github.aixtin.nyrallite.contract.ToolResult

/**
 * WASM 插件工具执行器：信任校验 → 元数据解析 → 带超时执行 → 结果提取。
 *
 * 超时保护：Chasm 的调用不可中断，超时后放弃执行线程（线程被死循环占用
 * 属预期代价，进程不会卡死）；每次调用独立线程，被放弃的线程不拖累后续调用。
 * 执行必须发生在前台或保活线程，后台进程被系统冻结时计时同样被冻结，不会误判超时。
 */
object WasmToolExecutor {

    /** 单次工具执行的默认上限；插件死循环不能拖死对话 */
    const val DEFAULT_TIMEOUT_MS = 10_000L

    fun call(
        pluginId: String,
        wasmBytes: ByteArray,
        trustedSha256: String?,
        argumentsJson: String,
        gateway: WasmPluginGateway,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ToolResult {
        val (store, instance) = when (val r = WasmPluginRuntime.loadTrusted(pluginId, wasmBytes, trustedSha256, gateway)) {
            is WasmPluginRuntime.LoadResult.Ok -> Pair(r.store, r.instance)
            is WasmPluginRuntime.LoadResult.Fail -> return ToolResult("插件 $pluginId 无法加载：${r.reason}", isError = true)
        }
        val meta = when (val m = parseWasmPluginMeta(wasmBytes)) {
            is WasmPluginMetaResult.Ok -> m.meta
            is WasmPluginMetaResult.Fail -> return ToolResult("插件 $pluginId 元数据校验失败：${m.reason}", isError = true)
            is WasmPluginMetaResult.None -> return ToolResult("插件 $pluginId 没有工具元数据，不能作为工具调用", isError = true)
        }

        val invocationBox = arrayOfNulls<String>(1)
        val worker = Thread {
            invocationBox[0] = WasmPluginRuntime.call(instance, store, meta.entryExport)
        }.apply {
            isDaemon = true
            start()
        }
        worker.join(timeoutMs)
        if (worker.isAlive) {
            return ToolResult("插件 $pluginId 执行超时（>${timeoutMs}ms），已放弃", isError = true)
        }

        val result = gateway.toolResult
            ?: return ToolResult("插件 $pluginId 未交回执行结果（调用 ${invocationBox[0]}）", isError = true)
        return ToolResult(result.toString(Charsets.UTF_8))
    }
}
