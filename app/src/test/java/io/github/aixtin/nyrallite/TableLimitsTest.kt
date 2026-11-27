package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.plugins.markdown.TableFiller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AGENTS.md 规则 4 的断言：表格上限文案与渲染实现必须同步。 */
class TableLimitsTest {

    @Test
    fun `table limits in prompt match renderer`() {
        val prompt = PromptDefaults.SYSTEM
        assertTrue("提示词缺少行上限文案", prompt.contains("100 行"))
        assertTrue("提示词缺少列上限文案", prompt.contains("10 列"))
        assertEquals(100, TableFiller.MAX_TABLE_ROWS)
        assertEquals(10, TableFiller.MAX_TABLE_COLS)
    }
}
