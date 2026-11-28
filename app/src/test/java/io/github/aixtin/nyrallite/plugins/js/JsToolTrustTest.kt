package io.github.aixtin.nyrallite.plugins.js

import org.junit.Assert.assertEquals
import org.junit.Test

class JsToolTrustTest {

    @Test
    fun `未登记视为草稿放行`() {
        assertEquals(
            JsToolTrustResult.Pass,
            JsToolTrust.check(registeredSha256 = null, actualSha256 = "abcd"),
        )
    }

    @Test
    fun `登记一致放行且忽略大小写`() {
        assertEquals(
            JsToolTrustResult.Pass,
            JsToolTrust.check(registeredSha256 = "ABCD", actualSha256 = "abcd"),
        )
    }

    @Test
    fun `登记不一致判定为已变更`() {
        assertEquals(
            JsToolTrustResult.Changed,
            JsToolTrust.check(registeredSha256 = "abcd", actualSha256 = "ffff"),
        )
    }
}
