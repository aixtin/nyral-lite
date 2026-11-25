package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryStore
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.WorkspaceStore
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.kernel.provide
import java.io.File

/**
 * 长期记忆插件：文件存储 + 三个工具（remember / memory_search / forget）。
 *
 * 不依赖网络，也不碰系统权限；索引页由主体在拼 system prompt 时注入。
 */
class MemoryPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "memory-store",
        permissions = setOf(Permission.FILES),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        ctx.require(Permission.FILES)
        val store = FileMemoryStore(File(ctx.appContext.filesDir, MEMORY_FILE))
        // 会话 id 只作来源标记，取不到不影响使用
        val workspace = ctx.services.getOrNull<WorkspaceStore>()
        val tools = MemoryToolSource(store) { workspace?.currentSessionId.orEmpty() }

        ctx.services.provide(MemoryStore::class, store, requires = setOf(Permission.FILES))
        ctx.services.provide(ToolSource::class, tools, requires = setOf(Permission.FILES))
    }

    private companion object {
        const val MEMORY_FILE = "memory/memories.json"
    }
}
