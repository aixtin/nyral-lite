package io.github.aixtin.nyrallite

import org.junit.Assert.assertEquals
import org.junit.Test

/** 更新版本比较的回归测试：钉住逐段数字比较，防止退化成字符串比较。 */
class UpdateCheckerTest {

    // —— 基础大小关系 ——

    @Test
    fun newerPatchWins() {
        assertEquals(1, UpdateChecker.compareVersion("0.1.1", "0.1.0"))
    }

    @Test
    fun olderLoses() {
        assertEquals(-1, UpdateChecker.compareVersion("0.1.0", "0.1.1"))
    }

    @Test
    fun equalReturnsZero() {
        assertEquals(0, UpdateChecker.compareVersion("0.1.1", "0.1.1"))
    }

    // —— 关键回归：多段数字必须逐位比，不能按字符串比 ——

    @Test
    fun tenGreaterThanNine() {
        assertEquals(1, UpdateChecker.compareVersion("0.3.10", "0.3.9"))
        assertEquals(-1, UpdateChecker.compareVersion("0.3.9", "0.3.10"))
    }

    @Test
    fun majorVersionBeatsPatch() {
        assertEquals(1, UpdateChecker.compareVersion("1.0.0", "0.9.9"))
    }

    // —— 前缀 v 与段数不齐 ——

    @Test
    fun vPrefixIgnored() {
        assertEquals(0, UpdateChecker.compareVersion("v0.1.1", "0.1.1"))
        assertEquals(1, UpdateChecker.compareVersion("v1.2", "0.9.9"))
    }

    @Test
    fun missingSegmentsTreatedAsZero() {
        assertEquals(0, UpdateChecker.compareVersion("1.2", "1.2.0"))
        assertEquals(0, UpdateChecker.compareVersion("1.2", "1.2.0.0"))
        assertEquals(1, UpdateChecker.compareVersion("1.2.1", "1.2"))
    }

    // —— 脏输入 ——

    @Test
    fun suffixAfterDigitsIgnored() {
        assertEquals(0, UpdateChecker.compareVersion("v0.1.1-beta", "0.1.1"))
    }

    @Test
    fun nonNumericSegmentsCountAsZero() {
        assertEquals(-1, UpdateChecker.compareVersion("abc", "0.0.1"))
    }

    @Test
    fun emptyStringIsZero() {
        assertEquals(1, UpdateChecker.compareVersion("0.1.0", ""))
        assertEquals(0, UpdateChecker.compareVersion("", ""))
    }
}
