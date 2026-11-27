package io.github.aixtin.nyrallite.plugins.wasm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WasmPluginGatewayTest {

    private fun demoWasm(): ByteArray =
        javaClass.getResourceAsStream("/plugins/demo.wasm")?.readBytes() ?: error("demo.wasm not found")

    private fun gateway(
        allowlist: Set<String> = setOf("/data/notes.txt"),
        provider: (String) -> ByteArray? = { path -> "plugin-read-ok".encodeToByteArray().takeIf { path == "/data/notes.txt" } },
    ) = WasmPluginGateway(readFileAllowlist = allowlist, fileProvider = provider)

    private fun setup(gw: WasmPluginGateway): Pair<io.github.charlietap.chasm.embedding.shapes.Store, io.github.charlietap.chasm.embedding.shapes.Instance> {
        return when (val r = WasmPluginRuntime.load(demoWasm(), gw)) {
            is WasmPluginRuntime.LoadResult.Ok -> Pair(r.store, r.instance)
            is WasmPluginRuntime.LoadResult.Fail -> error(r.reason)
        }
    }

    @Test
    fun `插件经网关读取白名单内文件`() {
        val gw = gateway()
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_allowed")
        assertTrue("应返回 14 字节，实际 $result", result.contains("14"))
        assertTrue(gw.auditLog.any { it.contains("ALLOWED") && it.contains("/data/notes.txt") })
    }

    @Test
    fun `网关拒绝白名单外文件`() {
        val gw = gateway()
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "run_denied")
        assertTrue("应返回 -1，实际 $result", result.contains("-1"))
        assertTrue(gw.auditLog.any { it.contains("DENIED") && it.contains("/etc/shadow") })
    }

    @Test
    fun `宿主写入内容可被插件读回`() {
        val gw = gateway()
        val (store, instance) = setup(gw)
        WasmPluginRuntime.call(instance, store, "run_allowed")
        val result = WasmPluginRuntime.call(instance, store, "readback")
        assertTrue(result.isNotEmpty())
        assertTrue(gw.capturedLogs.contains("plugin-read-ok"))
    }

    @Test
    fun `插件日志经网关捕获`() {
        val gw = gateway()
        val (store, instance) = setup(gw)
        WasmPluginRuntime.call(instance, store, "greet")
        assertEquals(listOf("hello-from-plugin"), gw.capturedLogs)
    }

    @Test
    fun `未声明的导出无法调用`() {
        val gw = gateway()
        val (store, instance) = setup(gw)
        val result = WasmPluginRuntime.call(instance, store, "exec_su")
        assertTrue("调用不存在的导出应失败，实际 $result", result.contains("失败"))
    }

    @Test
    fun `哈希校验拒绝未登记与不匹配的插件`() {
        val bytes = demoWasm()
        val gw = gateway()
        // 未登记
        val r1 = WasmPluginRuntime.loadTrusted("p1", bytes, null, gw)
        assertTrue(r1 is WasmPluginRuntime.LoadResult.Fail)
        // 哈希不匹配
        val r2 = WasmPluginRuntime.loadTrusted("p1", bytes, "deadbeef", gw)
        assertTrue(r2 is WasmPluginRuntime.LoadResult.Fail)
        // 匹配则通过
        val r3 = WasmPluginRuntime.loadTrusted("p1", bytes, WasmPluginRuntime.sha256(bytes), gw)
        assertTrue(r3 is WasmPluginRuntime.LoadResult.Ok)
    }
}
