package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.McpConfig
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.contract.ToolRegistry
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.kernel.GateBus
import io.github.aixtin.nyrallite.kernel.GateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ToolRegistry] 的远程 MCP 实现。
 *
 * 配置为空时对外表现为"没有工具"；地址变更时丢弃旧会话与工具缓存。
 * 每次真正调用工具前先过 GateBus（tool.before-call），安全策略可以拦截。
 */
class McpToolRegistry(
    private val configStore: McpConfigStore,
    private val gates: GateBus,
    private val pluginId: String,
) : ToolRegistry, ToolSource {

    private var session: McpSession? = null
    private var sessionConfig: McpConfig? = null
    private var tools: List<ToolSpec> = emptyList()
    private var toolsAt = 0L

    override suspend fun list(): List<ToolSpec> = withContext(Dispatchers.IO) {
        val current = session() ?: return@withContext emptyList()
        if (tools.isNotEmpty() && System.currentTimeMillis() - toolsAt < CACHE_MS) {
            return@withContext tools
        }
        try {
            tools = McpToolMapping.specsOf(current.listTools())
            toolsAt = System.currentTimeMillis()
            tools
        } catch (e: Exception) {
            // 会话可能已失效（服务端重启/过期），下次重走 initialize
            resetSession()
            throw e
        }
    }

    override suspend fun call(name: String, argumentsJson: String): ToolResult =
        withContext(Dispatchers.IO) {
            when (val gate = gates.check(ToolCallRequest(pluginId, name, argumentsJson))) {
                is GateResult.Block -> return@withContext ToolResult("安全策略拦截：${gate.reason}", true)
                GateResult.Allow -> Unit
            }
            val current = session() ?: return@withContext ToolResult("未配置 MCP 服务器地址", true)
            try {
                McpToolMapping.resultOf(current.callTool(name, argumentsJson))
            } catch (e: Exception) {
                resetSession()
                throw e
            }
        }

    /** ToolSource 实现：先扫描自己缓存的工具清单，命中才执行。 */
    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? =
        withContext(Dispatchers.IO) {
            val myTools = runCatching { tools }.getOrDefault(emptyList())
            if (myTools.none { it.name == name }) return@withContext null
            call(name, argumentsJson)
        }

    @Synchronized
    private fun session(): McpSession? {
        val config = configStore.load()
        if (!config.isConfigured) return null
        if (config != sessionConfig) {
            session = McpSession(config)
            sessionConfig = config
            tools = emptyList()
            toolsAt = 0L
        }
        return session
    }

    @Synchronized
    private fun resetSession() {
        session = null
        sessionConfig = null
        tools = emptyList()
        toolsAt = 0L
    }

    private companion object {
        /** 工具清单通常很稳定，缓存一分钟，避免每条消息都多两跳网络。 */
        const val CACHE_MS = 60_000L
    }
}
