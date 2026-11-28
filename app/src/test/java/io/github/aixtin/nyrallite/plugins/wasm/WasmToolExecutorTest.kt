package io.github.aixtin.nyrallite.plugins.wasm

import io.github.aixtin.nyrallite.contract.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具执行器：信任校验、参数与结果通道、超时保护 */
class WasmToolExecutorTest {

    private fun wasm(name: String): ByteArray =
        javaClass.getResourceAsStream("/plugins/$name")?.readBytes() ?: error("$name not found")

    private fun trusted(name: String): String = WasmPluginRuntime.sha256(wasm(name))

    @Test
    fun `参数经通道进入插件 结果经通道返回`() {
        val gw = WasmPluginGateway(argsProvider = { "{\"text\":\"hello\"}".encodeToByteArray() })
        val r = WasmToolExecutor.call(
            pluginId = "toolcall",
            wasmBytes = wasm("toolcall.wasm"),
            trustedSha256 = trusted("toolcall.wasm"),
            argumentsJson = "{\"text\":\"hello\"}",
            gateway = gw,
        )
        assertFalse("应执行成功：${r.content}", r.isError)
        assertEquals("{\"text\":\"hello\"}", r.content)
        assertTrue("参数应被插件日志记录", gw.capturedLogs.contains("{\"text\":\"hello\"}"))
    }

    @Test
    fun `未登记信任的插件拒绝执行`() {
        val gw = WasmPluginGateway(argsProvider = { null })
        val r = WasmToolExecutor.call(
            pluginId = "toolcall",
            wasmBytes = wasm("toolcall.wasm"),
            trustedSha256 = null,
            argumentsJson = "{}",
            gateway = gw,
        )
        assertTrue("应拒绝：${r.content}", r.isError)
        assertTrue(r.content.contains("未登记信任"))
    }

    @Test
    fun `哈希不符的插件拒绝执行`() {
        val gw = WasmPluginGateway(argsProvider = { null })
        val r = WasmToolExecutor.call(
            pluginId = "toolcall",
            wasmBytes = wasm("toolcall.wasm"),
            trustedSha256 = "deadbeef".repeat(8),
            argumentsJson = "{}",
            gateway = gw,
        )
        assertTrue("应拒绝：${r.content}", r.isError)
        assertTrue(r.content.contains("哈希"))
    }

    @Test
    fun `死循环插件被超时保护掐断`() {
        val gw = WasmPluginGateway(argsProvider = { null })
        val start = System.currentTimeMillis()
        val r = WasmToolExecutor.call(
            pluginId = "spin",
            wasmBytes = wasm("spin.wasm"),
            trustedSha256 = trusted("spin.wasm"),
            argumentsJson = "{}",
            gateway = gw,
            timeoutMs = 500,
        )
        val elapsed = System.currentTimeMillis() - start
        assertTrue("应超时失败：${r.content}", r.isError)
        assertTrue(r.content.contains("超时"))
        assertTrue("应在超时附近返回，实际 ${elapsed}ms", elapsed in 400..5000)
    }

    @Test
    fun `无元数据的插件不能作为工具调用`() {
        val gw = WasmPluginGateway()
        val r = WasmToolExecutor.call(
            pluginId = "demo",
            wasmBytes = wasm("demo.wasm"),
            trustedSha256 = trusted("demo.wasm"),
            argumentsJson = "{}",
            gateway = gw,
        )
        assertTrue("应失败：${r.content}", r.isError)
        assertTrue(r.content.contains("没有工具元数据"))
    }

    @Test
    fun `put_result 超上限的结果整体拒绝`() {
        // 插件内存里没有有效结果时 gateway.toolResult 为 null，执行器报未交回
        val gw = WasmPluginGateway(argsProvider = { null })
        val r = WasmToolExecutor.call(
            pluginId = "toolcall",
            wasmBytes = wasm("toolcall.wasm"),
            trustedSha256 = trusted("toolcall.wasm"),
            argumentsJson = "",
            gateway = gw,
        )
        // get_args 返回 0 长度，插件 put_result(8192, 0) 交回空结果
        assertEquals(ToolResult(content = ""), r)
    }
}
