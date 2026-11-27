package io.github.aixtin.nyrallite.plugins.wasm

import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 能力扩展：notify 与 http_fetch 经网关放行/拒绝的行为 */
class WasmPluginGatewayM2Test {

    private fun m2Wasm(): ByteArray =
        javaClass.getResourceAsStream("/plugins/m2demo.wasm")?.readBytes() ?: error("m2demo.wasm not found")

    private fun setup(gw: WasmPluginGateway): Pair<Store, Instance> {
        return when (val r = WasmPluginRuntime.load(m2Wasm(), gw)) {
            is WasmPluginRuntime.LoadResult.Ok -> Pair(r.store, r.instance)
            is WasmPluginRuntime.LoadResult.Fail -> error(r.reason)
        }
    }

    @Test
    fun `通知经网关放行并审计`() {
        val notified = mutableListOf<Pair<String, String>>()
        val gw = WasmPluginGateway(notifier = { t, b -> notified += t to b; true })
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_notify")
        assertTrue("应返回 0，实际 $result", result.contains("0"))
        assertEquals(listOf("Nyral Plugin" to "hello from m2demo"), notified)
        assertTrue(gw.auditLog.any { it.contains("notify") && it.contains("ALLOWED") })
    }

    @Test
    fun `未注入通知能力时拒绝`() {
        val gw = WasmPluginGateway()
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_notify")
        assertTrue("应返回 -1，实际 $result", result.contains("-1"))
        assertTrue(gw.auditLog.any { it.contains("notify") && it.contains("DENIED") })
    }

    @Test
    fun `网络请求经网关放行并写入插件内存`() {
        val gw = WasmPluginGateway(httpFetcher = { url -> if (url == "https://example.com/api") "m2-ok!".encodeToByteArray() else null })
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_fetch")
        assertTrue("应返回 6，实际 $result", result.contains("6"))
        WasmPluginRuntime.call(instance, store, "readback")
        assertTrue("插件应能读回响应体", gw.capturedLogs.contains("m2-ok!"))
        assertTrue(gw.auditLog.any { it.contains("http_fetch") && it.contains("ALLOWED") })
    }

    @Test
    fun `未注入网络能力时拒绝`() {
        val gw = WasmPluginGateway()
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_fetch")
        assertTrue("应返回 -1，实际 $result", result.contains("-1"))
        assertTrue(gw.auditLog.any { it.contains("http_fetch") && it.contains("DENIED") })
    }
}
