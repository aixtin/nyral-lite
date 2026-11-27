package io.github.aixtin.nyrallite.plugins.security

import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.kernel.PluginConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 门禁判定顺序的回归测试：改判定逻辑前先看这里，别靠真机试错。 */
class ToolPolicyTest {

    private fun config(vararg pairs: Pair<String, String>): PluginConfig {
        val map = pairs.toMap()
        return object : PluginConfig {
            override fun getString(key: String, def: String): String = map[key] ?: def
            override fun put(key: String, value: String) = throw UnsupportedOperationException()
            override fun remove(key: String) = throw UnsupportedOperationException()
        }
    }

    private fun rules(vararg pairs: Pair<String, String>) = PolicyRules.from(config(*pairs))

    // —— 判定顺序：关 > 禁止 > 白名单 > 严格档拒绝 > 预置白名单 > 确认 ——

    @Test
    fun offModeAllowsEverythingEvenDenyListed() {
        val r = rules(SecurityConfigKeys.MODE to "off", SecurityConfigKeys.DENY to "run_shell")
        assertEquals(PolicyVerdict.Allow, r.decide("run_shell"))
        assertEquals(PolicyVerdict.Allow, r.decide("anything"))
    }

    @Test
    fun denyBeatsAllowAndDefaultAllow() {
        val r = rules(
            SecurityConfigKeys.MODE to "auto",
            SecurityConfigKeys.ALLOW to "memory_search, run_shell",
            SecurityConfigKeys.DENY to "run_shell\nmemory_search",
        )
        assertEquals(PolicyVerdict.Deny("工具 [run_shell] 在禁止名单中"), r.decide("run_shell"))
        assertTrue(r.decide("memory_search") is PolicyVerdict.Deny)
    }

    @Test
    fun autoAllowsDefaultReadonlyToolsWithoutConfirm() {
        val r = rules(SecurityConfigKeys.MODE to "auto")
        assertEquals(PolicyVerdict.Allow, r.decide("memory_search"))
        assertEquals(PolicyVerdict.Allow, r.decide("history_search"))
    }

    @Test
    fun autoSendsUnknownToolsToConfirm() {
        val r = rules(SecurityConfigKeys.MODE to "auto")
        assertEquals(PolicyVerdict.Confirm, r.decide("read_clipboard"))
        assertEquals(PolicyVerdict.Confirm, r.decide("mcp_weather"))
    }

    @Test
    fun autoAllowsExplicitWhitelist() {
        val r = rules(SecurityConfigKeys.MODE to "auto", SecurityConfigKeys.ALLOW to "read_clipboard")
        assertEquals(PolicyVerdict.Allow, r.decide("read_clipboard"))
    }

    @Test
    fun strictDeniesAnythingOutsideLiteralWhitelist() {
        val r = rules(SecurityConfigKeys.MODE to "strict", SecurityConfigKeys.ALLOW to "history_search")
        assertEquals(PolicyVerdict.Allow, r.decide("history_search"))
        // 预置白名单在严格档不生效
        assertTrue(r.decide("memory_search") is PolicyVerdict.Deny)
        // 严格档不弹确认，名单外一律拒
        assertTrue(r.decide("read_clipboard") is PolicyVerdict.Deny)
    }

    // —— 档位解析 ——

    @Test
    fun modeParsingIsLenient() {
        assertEquals(SecurityMode.OFF, SecurityMode.of("OFF"))
        assertEquals(SecurityMode.OFF, SecurityMode.of(" off "))
        assertEquals(SecurityMode.STRICT, SecurityMode.of("Strict"))
        assertEquals(SecurityMode.AUTO, SecurityMode.of("unknown"))
    }

    @Test
    fun defaultModeIsAutoWhenUnset() {
        assertEquals(SecurityMode.AUTO, rules().mode)
    }

    // —— 名单解析 ——

    @Test
    fun listsParseCommasNewlinesAndBlankEntries() {
        val r = rules(
            SecurityConfigKeys.ALLOW to " a , b\n\nc  ,\n",
            SecurityConfigKeys.DENY to " , ",
        )
        assertEquals(setOf("a", "b", "c"), r.allow)
        assertTrue(r.deny.isEmpty())
    }
}
