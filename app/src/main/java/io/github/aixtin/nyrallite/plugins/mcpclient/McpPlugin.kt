package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.McpProbe
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.kernel.provide

/**
 * 标准插件：远程 MCP 客户端。
 * 只负责把第三方 MCP 工具接进来，注册为 [ToolSource] 供 MultiToolRegistry 聚合。
 * 不碰聊天界面与传输协议。
 */
class McpPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "mcp-client",
        permissions = setOf(Permission.NETWORK),
        dependsOn = setOf("storage-prefs"),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        ctx.require(Permission.NETWORK)

        val configStore = ctx.services.get<McpConfigStore>()
        val mcpSource = McpToolRegistry(configStore, ctx.gates, ctx.pluginId)
        ctx.services.provide(
            ToolSource::class,
            mcpSource,
            requires = setOf(Permission.NETWORK),
        )
        ctx.services.provide(
            McpProbe::class,
            McpProbeClient(),
            requires = setOf(Permission.NETWORK),
        )
    }
}
