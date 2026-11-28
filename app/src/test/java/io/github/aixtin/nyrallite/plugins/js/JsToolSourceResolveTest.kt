package io.github.aixtin.nyrallite.plugins.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsToolSourceResolveTest {

    @Test
    fun `正常名字解析到工具id`() {
        assertEquals(
            "ip_probe",
            JsToolSource.resolveToolId(listOf("ip_probe"), "js_ip_probe"),
        )
    }

    @Test
    fun `清洗撞名按字母序先到先得`() {
        assertEquals(
            "a b",
            JsToolSource.resolveToolId(listOf("a b", "a.b"), "js_a_b"),
        )
    }

    @Test
    fun `未匹配返回null`() {
        assertNull(JsToolSource.resolveToolId(listOf("ip_probe"), "js_other"))
        assertNull(JsToolSource.resolveToolId(emptyList(), "js_ip_probe"))
    }
}
