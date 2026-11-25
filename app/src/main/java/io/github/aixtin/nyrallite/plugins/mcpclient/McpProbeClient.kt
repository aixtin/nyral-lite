package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.McpConfig
import io.github.aixtin.nyrallite.contract.McpProbe
import io.github.aixtin.nyrallite.contract.McpProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [McpProbe] 实现：新建一次性会话握手，不复用常驻会话与工具缓存。 */
internal class McpProbeClient : McpProbe {

    override suspend fun probe(config: McpConfig): McpProbeResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext McpProbeResult(emptyList(), "请先填写服务器地址")
        }
        try {
            val tools = McpToolMapping.specsOf(McpSession(config).listTools())
            McpProbeResult(tools)
        } catch (e: Exception) {
            McpProbeResult(emptyList(), e.message ?: e.toString())
        }
    }
}
