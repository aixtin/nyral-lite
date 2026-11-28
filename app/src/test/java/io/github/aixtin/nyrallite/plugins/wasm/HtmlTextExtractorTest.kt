package io.github.aixtin.nyrallite.plugins.wasm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正文提取纯函数测试：fixture 字符串，不做网络请求 */
class HtmlTextExtractorTest {

    private val articleHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>测试文章</title><style>body{color:red}</style>
        <script>var tracking = "should-not-appear";</script></head>
        <body>
        <nav><a href="/">首页</a><a href="/about">关于</a></nav>
        <article><h1>标题</h1>
        <p>第一段正文内容，讲的是抓取器升级的故事。</p>
        <p>第二段正文内容，Readability 算法应该保留这里。</p>
        </article>
        <footer>版权所有 广告位</footer>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `html 提取出正文且剥掉脚本`() {
        val text = HtmlTextExtractor.extract(articleHtml)
        assertTrue(text != null)
        assertTrue("正文应包含标题", text!!.contains("标题"))
        assertTrue("正文应包含第一段", text.contains("第一段正文内容"))
        assertTrue("脚本内容不得出现", !text.contains("should-not-appear"))
        assertTrue("样式内容不得出现", !text.contains("color:red"))
    }

    @Test
    fun `readability 失败时回落 jsoup 剥标签`() {
        // 无语义标签的碎片页：readability 可能提不出正文，回落路径应仍有产出
        val messy = "<html><body><div>碎片<em>文字</em>内容</div></body></html>"
        val text = HtmlTextExtractor.extract(messy)
        assertTrue(text != null)
        assertTrue(text!!.contains("碎片"))
        assertTrue(!text.contains("<"))
    }

    @Test
    fun `非 html 输入原样返回 null`() {
        assertNull(HtmlTextExtractor.extract("{\"items\":[1,2,3]}"))
        assertNull(HtmlTextExtractor.extract("<?xml version=\"1.0\"?><rss><channel/></rss>"))
        assertNull(HtmlTextExtractor.extract("just plain text"))
        assertNull(HtmlTextExtractor.extract(""))
    }

    @Test
    fun `连续空行压缩为最多一个空行`() {
        val html = "<html><body><p>甲</p><p>乙</p><p>丙</p></body></html>"
        val text = HtmlTextExtractor.extract(html)!!
        assertTrue(!text.contains("\n\n\n"))
    }

    @Test
    fun `utf8 截断不拆散多字节字符`() {
        val text = "中文".repeat(3000) // 18000 字节，全部三字节字符
        val truncated = HtmlTextExtractor.truncateUtf8(text, 4096)
        assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= 4096)
        assertTrue(!truncated.endsWith('�'))
        assertEquals("中", truncated.substring(truncated.length - 1))
    }

    @Test
    fun `短文本截断原样返回`() {
        assertEquals("hello", HtmlTextExtractor.truncateUtf8("hello", 4096))
    }

    @Test
    fun `head 超过 4KB 的页面仍能提取出正文`() {
        // 回归：抓取器曾按 4KB 截断原始 HTML 再提取，head（样式/脚本）超 4KB 时正文全丢。
        // 提取器本身必须吃完整页面——这里用大 head fixture 保证提取路径不依赖头部截断
        val bigHead = "<head><style>" + "body{margin:0;padding:0}" .repeat(300) + "</style></head>"
        assertTrue(bigHead.length > 4096)
        val html = "<!DOCTYPE html><html>$bigHead<body><article><p>正文干货在这里。</p></article></body></html>"
        val text = HtmlTextExtractor.extract(html)
        assertTrue(text != null && text.contains("正文干货在这里"))
    }
}
