package io.github.aixtin.nyrallite.plugins.memorystore

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryDedupTest {

    @Test
    fun dropsRestatementOfKnownMemory() {
        val kept = MemoryDedup.filter(
            known = listOf("The user's project name is Nyral Lite."),
            candidates = listOf("项目" to "The user's project name is Nyral Lite"),
        )

        assertEquals(0, kept.size)
    }

    @Test
    fun dropsDuplicateWithinSameBatch() {
        val kept = MemoryDedup.filter(
            known = emptyList(),
            candidates = listOf(
                "项目" to "用户的项目名称叫 Nyral Lite",
                "项目" to "用户的项目名称叫 Nyral Lite。",
            ),
        )

        assertEquals(1, kept.size)
    }

    @Test
    fun keepsNewFactAboutSameTopic() {
        val kept = MemoryDedup.filter(
            known = listOf("用户的项目名称叫 Nyral Lite"),
            candidates = listOf("主题" to "用户希望 Nyral Lite 默认使用深色主题"),
        )

        assertEquals(1, kept.size)
    }
}
