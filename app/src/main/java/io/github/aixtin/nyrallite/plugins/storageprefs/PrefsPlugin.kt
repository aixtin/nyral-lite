package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.McpConfigStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.WorkspaceStore
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.provide
import java.io.File

/**
 * 标准插件：本地存储。
 * 配置走 SharedPreferences，会话正文落在应用私有的 files/sessions 下；
 * 两者都在私有目录，不访问外部存储。
 */
class PrefsPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "storage-prefs",
        builtin = true,
        permissions = setOf(Permission.FILES),
    )

    override fun install(ctx: PluginContext) {
        ctx.require(Permission.FILES)
        val dir = File(ctx.appContext.filesDir, SESSION_DIR)
        val sessions = FileSessionStore(dir)
        val workspace = FileWorkspaceStore(dir)
        ctx.services.provide(ApiConfigStore::class, SharedPrefsApiConfigStore(ctx.appContext))
        ctx.services.provide(McpConfigStore::class, SharedPrefsMcpConfigStore(ctx.appContext))
        ctx.services.provide(SessionStore::class, sessions)
        ctx.services.provide(WorkspaceStore::class, workspace)
        // 摘要只记要点，原文由这个工具按需回读
        ctx.services.provide(
            ToolSource::class,
            HistoryToolSource(sessions) { workspace.currentSessionId.orEmpty() },
            requires = setOf(Permission.FILES),
        )
    }

    private companion object {
        /** 会话目录名，位于应用私有 files 目录下。 */
        const val SESSION_DIR = "sessions"
    }
}
