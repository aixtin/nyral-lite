package io.github.aixtin.nyrallite.plugins.markdown

import io.github.aixtin.nyrallite.contract.ContentRenderer
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.provide

/**
 * 标准插件：Markdown 渲染。
 * 全文只注册一个 [ContentRenderer]，流式与收尾走同一实现，杜绝双路径跳变。
 */
class MarkdownPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "render-markdown",
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        ctx.services.provide(ContentRenderer::class, MarkdownContentRenderer())
    }
}
