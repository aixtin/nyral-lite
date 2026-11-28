package io.github.aixtin.nyrallite.plugins.wasm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型侧工具名生成：字符白名单与长度上限由模型 API 决定，冒号和点号都会被整批 400 拒绝 */
class WasmToolNameTest {

    @Test
    fun `常规插件名直接拼接`() {
        assertEquals("wasm_toolcall_echo", WasmToolSource.toolName("toolcall", "echo"))
    }

    @Test
    fun `插件名中的冒号点号等非法字符被清洗`() {
        val n = WasmToolSource.toolName("my.plugin:v2", "echo")
        assertEquals("wasm_my_plugin_v2_echo", n)
        assertTrue("只允许字母数字下划线短横线", n.matches(Regex("[a-zA-Z0-9_-]+")))
    }

    @Test
    fun `超长插件名截断并追加短哈希且不超上限`() {
        val long = "p".repeat(100)
        val n = WasmToolSource.toolName(long, "echo")
        assertTrue("超长应被压到上限内，实际 ${n.length}", n.length <= WasmToolSource.MAX_TOOL_NAME)
        // 不同超长插件名不碰撞
        val other = WasmToolSource.toolName("q".repeat(100), "echo")
        assertTrue(n != other)
    }
}
