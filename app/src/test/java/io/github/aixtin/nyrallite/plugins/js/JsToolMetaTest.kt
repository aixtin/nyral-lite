package io.github.aixtin.nyrallite.plugins.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JS 工具元数据解析与校验：脚本头部注释块视为不可信输入 */
class JsToolMetaTest {

    private fun script(meta: String): String = """
        /* nyral.tool $meta */
        function tool_call() { return "hi"; }
        tool_call();
    """.trimIndent()

    @Test
    fun `解析合法元数据`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","description":"回显工具","contractVersion":"1.0","capabilities":["http_fetch"]}"""),
        )
        assertTrue("应为 Ok，实际 $r", r is JsToolMetaResult.Ok)
        r as JsToolMetaResult.Ok
        assertEquals("echo", r.meta.name)
        assertEquals("回显工具", r.meta.description)
        assertEquals("1.0", r.meta.contractVersion)
        assertEquals(listOf("http_fetch"), r.meta.capabilities)
    }

    @Test
    fun `缺少元数据块被拒`() {
        val r = parseJsToolMeta("function tool_call() { return 1; }")
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("nyral.tool"))
    }

    @Test
    fun `非严格 JSON 被拒`() {
        // 单引号键：org.json 会放行，严格校验必须拦下
        val r = parseJsToolMeta(script("{'name':'echo','description':'d','contractVersion':'1.0'}"))
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("JSON"))
    }

    @Test
    fun `契约大版本不匹配被拒`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","description":"d","contractVersion":"2.0","capabilities":[]}"""),
        )
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("大版本"))
    }

    @Test
    fun `契约小版本不拦`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","description":"d","contractVersion":"1.9","capabilities":[]}"""),
        )
        assertTrue("小版本仅记录不拦截，实际 $r", r is JsToolMetaResult.Ok)
    }

    @Test
    fun `能力名不合法被拒`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","description":"d","contractVersion":"1.0","capabilities":["Bad-Cap"]}"""),
        )
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("能力名"))
    }

    @Test
    fun `宿主未实现的能力被拒`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","description":"d","contractVersion":"1.0","capabilities":["file_read"]}"""),
        )
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("未实现"))
    }

    @Test
    fun `工具名超长被拒`() {
        val longName = "a".repeat(65)
        val r = parseJsToolMeta(
            script("""{"name":"$longName","description":"d","contractVersion":"1.0","capabilities":[]}"""),
        )
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("工具名"))
    }

    @Test
    fun `描述缺失被拒`() {
        val r = parseJsToolMeta(
            script("""{"name":"echo","contractVersion":"1.0","capabilities":[]}"""),
        )
        assertTrue(r is JsToolMetaResult.Fail)
        assertTrue((r as JsToolMetaResult.Fail).reason.contains("描述"))
    }
}
