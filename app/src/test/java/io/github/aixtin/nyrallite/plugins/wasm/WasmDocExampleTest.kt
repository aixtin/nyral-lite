package io.github.aixtin.nyrallite.plugins.wasm

import io.github.aixtin.nyrallite.contract.ToolResult
import java.io.File
import kotlin.io.path.createTempFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * 文档示例回归：docs/wasm-plugin-dev.md 引用的两个示例
 * （examples/wasm/echo_tool.wat 与 examples/wasm/echo_tool_rs/）
 * 必须始终能通过安装期校验并正确执行。示例改动而文档未同步时在这里失败。
 *
 * WAT 示例依赖 wat2wasm；Rust 示例依赖 cargo 与 wasm32-unknown-unknown 目标，
 * 环境缺失时跳过（不视为失败）。
 */
class WasmDocExampleTest {

    private fun repoFile(relative: String): File {
        val candidates = listOf(relative, "../$relative")
        for (c in candidates) {
            val f = File(System.getProperty("user.dir"), c)
            if (f.exists()) return f
        }
        error("找不到 $relative，尝试过：$candidates（user.dir=${System.getProperty("user.dir")}）")
    }

    private fun compileWat(wat: File): ByteArray {
        val out = createTempFile(prefix = "echo", suffix = ".wasm").toFile()
        val process = ProcessBuilder("wat2wasm", "--enable-annotations", wat.absolutePath, "-o", out.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        assertEquals("wat2wasm 应成功: $output", 0, process.waitFor())
        return out.readBytes()
    }

    private fun buildRust(): ByteArray {
        val cargo = listOf("cargo", "${System.getProperty("user.home")}/.cargo/bin/cargo").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false)
        } ?: run {
            Assume.assumeTrue("cargo 不可用，跳过 Rust 示例回归", false)
            error("unreachable")
        }
        val projectDir = repoFile("examples/wasm/echo_tool_rs")
        val process = ProcessBuilder(cargo, "build", "--target", "wasm32-unknown-unknown", "--release")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        assertEquals("cargo build 应成功: $output", 0, process.waitFor())
        return File(projectDir, "target/wasm32-unknown-unknown/release/echo_tool.wasm").readBytes()
    }

    private fun assertMetaOk(bytes: ByteArray) {
        val r = parseWasmPluginMeta(bytes)
        assertTrue("应为 Ok，实际 $r", r is WasmPluginMetaResult.Ok)
        r as WasmPluginMetaResult.Ok
        assertEquals("echo", r.meta.toolName)
        assertEquals("tool_call", r.meta.entryExport)
        assertTrue(r.meta.parametersJson.contains("\"text\""))
    }

    private fun assertEcho(bytes: ByteArray) {
        val gw = WasmPluginGateway(argsProvider = { "{\"text\":\"hello\"}".encodeToByteArray() })
        val r: ToolResult = WasmToolExecutor.call(
            pluginId = "echo_tool",
            wasmBytes = bytes,
            trustedSha256 = WasmPluginRuntime.sha256(bytes),
            argumentsJson = "{\"text\":\"hello\"}",
            gateway = gw,
        )
        assertFalse("应执行成功：${r.content}", r.isError)
        assertEquals("{\"text\":\"hello\"}", r.content)
        assertTrue("日志应被捕获", gw.capturedLogs.contains("echo_tool called"))
    }

    @Test
    fun `WAT 示例通过安装期元数据校验`() = assertMetaOk(compileWat(repoFile("examples/wasm/echo_tool.wat")))

    @Test
    fun `WAT 示例作为工具执行时原样回显参数`() = assertEcho(compileWat(repoFile("examples/wasm/echo_tool.wat")))

    @Test
    fun `Rust 示例通过安装期元数据校验`() = assertMetaOk(buildRust())

    @Test
    fun `Rust 示例作为工具执行时原样回显参数`() = assertEcho(buildRust())
}
