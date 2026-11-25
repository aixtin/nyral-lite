package io.github.aixtin.nyrallite.plugins.modelopenai

import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.kernel.provide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 标准插件：OpenAI 兼容流式传输。
 * 只负责协议（HTTP + SSE），system prompt / 会话由主体提供。
 */
class OpenAiPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "model-openai",
        permissions = setOf(Permission.NETWORK),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        // 声明校验：manifest 没写 NETWORK 就装不上
        ctx.require(Permission.NETWORK)

        val configStore = ctx.services.get<ApiConfigStore>()
        val attachments = ctx.services.get<AttachmentStores>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ctx.effect { scope.cancel() }

        ctx.services.provide(
            ChatTransport::class,
            OpenAiTransport(configStore, scope, attachments::open),
            requires = setOf(Permission.NETWORK),
        )
    }
}
