package io.github.aixtin.nyrallite

import org.junit.Assert.assertEquals
import org.junit.Test

/** 版本比较必须是数字逐段比较：字符串序会把 v0.3.10 判成小于 v0.3.9，导致更新提示失效。 */
class UpdateCheckerTest {

    @Test
    fun comparesPatchNumbersNumerically() {
        assertEquals(1, UpdateChecker.compareVersion("v0.3.10", "v0.3.9"))
        assertEquals(-1, UpdateChecker.compareVersion("v0.3.9", "v0.3.10"))
    }

    @Test
    fun treatsMissingSegmentsAsZero() {
        assertEquals(0, UpdateChecker.compareVersion("v0.2", "0.2.0"))
        assertEquals(1, UpdateChecker.compareVersion("1.2.1", "1.2"))
    }

    @Test
    fun comparesMajorAndMinorFirst() {
        assertEquals(1, UpdateChecker.compareVersion("v1.0.0", "v0.9.9"))
        assertEquals(-1, UpdateChecker.compareVersion("v0.9.0", "v1.0.0"))
    }

    @Test
    fun sameVersionIsEqual() {
        assertEquals(0, UpdateChecker.compareVersion("v0.1.0", "0.1.0"))
    }
}
