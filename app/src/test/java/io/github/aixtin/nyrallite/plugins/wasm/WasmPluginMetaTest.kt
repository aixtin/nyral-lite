package io.github.aixtin.nyrallite.plugins.wasm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具元数据解析与校验：自定义段内容视为不可信输入 */
class WasmPluginMetaTest {

    private fun wasm(name: String): ByteArray =
        javaClass.getResourceAsStream("/plugins/$name")?.readBytes() ?: error("$name not found")

    @Test
    fun `解析合法元数据`() {
        val r = parseWasmPluginMeta(wasm("toolcall.wasm"))
        assertTrue("应为 Ok，实际 $r", r is WasmPluginMetaResult.Ok)
        r as WasmPluginMetaResult.Ok
        assertEquals("echo", r.meta.toolName)
        assertEquals("tool_call", r.meta.entryExport)
        assertEquals("Echo back the call arguments (test plugin)", r.meta.description)
        assertTrue(r.meta.parametersJson.contains("\"type\""))
    }

    @Test
    fun `无自定义段返回 None`() {
        val r = parseWasmPluginMeta(wasm("demo.wasm"))
        assertEquals(WasmPluginMetaResult.None, r)
    }

    @Test
    fun `同长度篡改工具名为大写被拒`() {
        val bytes = wasm("toolcall.wasm")
        val patched = bytes.toString(Charsets.ISO_8859_1)
            .replace("\"name\":\"echo\"", "\"name\":\"Echo\"")
            .toByteArray(Charsets.ISO_8859_1)
        assertEquals("补丁应保持文件长度不变", bytes.size, patched.size)
        val r = parseWasmPluginMeta(patched)
        assertTrue("应因工具名不合法被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("工具名"))
    }

    @Test
    fun `自定义段不是 JSON 被拒`() {
        val bytes = wasm("toolcall.wasm")
        val patched = bytes.toString(Charsets.ISO_8859_1)
            .replace("\"name\":\"echo\"", "\"name\": echo ")
            .toByteArray(Charsets.ISO_8859_1)
        assertEquals("补丁应保持文件长度不变", bytes.size, patched.size)
        val r = parseWasmPluginMeta(patched)
        assertTrue("应因 JSON 不合法被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("JSON"))
    }

    @Test
    fun `入口导出不存在的插件被拒`() {
        // entry 指向 no_such_export，模块里没有这个导出
        val wat = """
            (module
              (memory (export "memory") 1)
              (@custom "nyral.tool" "{\"name\":\"ghost\",\"description\":\"entry points nowhere\",\"parameters\":{},\"entry\":\"no_such_export\"}")
              (func (export "tool_call") (result i32) (i32.const 0))
            )
        """.trimIndent()
        val bytes = compileWat(wat)
        val r = parseWasmPluginMeta(bytes)
        assertTrue("应因入口不存在被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("入口"))
    }

    @Test
    fun `无引号字符串值被拒`() {
        // org.json 会把无引号的 echo 宽容地解析成字符串，严格校验必须拦下
        val wat = moduleWithMeta("{\"name\": echo ,\"description\":\"d\",\"parameters\":{},\"entry\":\"tool_call\"}")
        val r = parseWasmPluginMeta(compileWat(wat))
        assertTrue("应因 JSON 不合法被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("JSON"))
    }

    @Test
    fun `单引号键值被拒`() {
        val wat = moduleWithMeta("{'name':'echo','description':'d','parameters':{},'entry':'tool_call'}")
        val r = parseWasmPluginMeta(compileWat(wat))
        assertTrue("应因 JSON 不合法被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("JSON"))
    }

    @Test
    fun `尾随逗号被拒`() {
        val wat = moduleWithMeta("{\"name\":\"echo\",\"description\":\"d\",\"parameters\":{},\"entry\":\"tool_call\",}")
        val r = parseWasmPluginMeta(compileWat(wat))
        assertTrue("应因 JSON 不合法被拒，实际 $r", r is WasmPluginMetaResult.Fail)
        assertTrue((r as WasmPluginMetaResult.Fail).reason.contains("JSON"))
    }

    @Test
    fun `带空白的合法元数据不误伤`() {
        val wat = moduleWithMeta(
            // \n \t 用 WAT 转义写入，顺带覆盖转义内容的严格解析
            "{ \"name\" : \"echo\" ,\\n\\t\"description\" : \"d\" , \"parameters\" : {} , \"entry\" : \"tool_call\" } ",
        )
        val r = parseWasmPluginMeta(compileWat(wat))
        assertTrue("空白合法 JSON 应通过，实际 $r", r is WasmPluginMetaResult.Ok)
    }

    /** 最小模块 + 指定元数据文本（WAT 转义由调用方负责） */
    private fun moduleWithMeta(metaJson: String): String = """
        (module
          (memory (export "memory") 1)
          (@custom "nyral.tool" "${metaJson.replace("\"", "\\\"")}")
          (func (export "tool_call") (result i32) (i32.const 0))
        )
    """.trimIndent()

    private fun compileWat(wat: String): ByteArray {
        val file = kotlin.io.path.createTempFile(prefix = "meta", suffix = ".wat").toFile()
        file.writeText(wat)
        val out = kotlin.io.path.createTempFile(prefix = "meta", suffix = ".wasm").toFile()
        val process = ProcessBuilder("wat2wasm", "--enable-annotations", file.absolutePath, "-o", out.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        assertEquals("wat2wasm 应成功: $output", 0, process.waitFor())
        return out.readBytes()
    }
}
