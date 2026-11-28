package io.github.aixtin.nyrallite.plugins.wasm

import org.junit.Test

/** 临时诊断：真实页面提取结果落盘查看 */
class ExtractDiagTest {
    @Test
    fun dump() {
        val html = java.io.File("/tmp/nyral_page.html").readText()
        val out = HtmlTextExtractor.extract(html)
        java.io.File("/tmp/extract_out.txt").writeText(out ?: "NULL")
    }
}
