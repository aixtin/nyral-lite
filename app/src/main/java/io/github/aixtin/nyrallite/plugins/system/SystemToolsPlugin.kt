package io.github.aixtin.nyrallite.plugins.system

import android.content.Context
import io.github.aixtin.nyrallite.ForegroundDispatcher
import io.github.aixtin.nyrallite.contract.ClipboardContract
import io.github.aixtin.nyrallite.contract.ForegroundContract
import io.github.aixtin.nyrallite.contract.IntentContract
import io.github.aixtin.nyrallite.contract.LocationContract
import io.github.aixtin.nyrallite.contract.NotificationContract
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.provide

/**
 * 系统能力插件：把 Android 原生能力封装成契约接口 + ToolSource。
 *
 * 契约层（NotificationContract / IntentContract / ForegroundContract）是纯接口，
 * 宿主实现，插件只注册服务，不直接 import android.*。
 *
 * 系统工具（send_notification / dial_phone / open_browser / share_text / start_timer）
 * 和 MCP 远程工具平级，由 MultiToolRegistry 聚合。
 */
class SystemToolsPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "system-tools",
        permissions = setOf(Permission.FILES),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        val appContext = ctx.appContext

        val notifier = NotificationSenderImpl(appContext)
        val dispatcher = IntentDispatcherImpl(appContext)
        val foregroundDispatcher = ForegroundDispatcher(appContext)
        val toolSource = SystemToolSource(notifier, dispatcher, foregroundDispatcher)

        // 设备能力：定位 + 剪贴板（容器型应用拿不到的原生能力）
        val location = LocationProviderImpl(appContext)
        val clipboard = ClipboardManagerImpl(appContext)
        val deviceTools = DeviceToolSource(location, clipboard)

        // 注册契约实现（供其他插件消费，或以后替换）
        ctx.services.provide(NotificationContract::class, notifier, requires = emptySet())
        ctx.services.provide(IntentContract::class, dispatcher, requires = emptySet())
        ctx.services.provide(ForegroundContract::class, foregroundDispatcher, requires = emptySet())
        ctx.services.provide(LocationContract::class, location, requires = emptySet())
        ctx.services.provide(ClipboardContract::class, clipboard, requires = emptySet())
        // ForegroundDispatcher 也是给 Service 取任务的通道
        ctx.services.provide(ForegroundDispatcher::class, foregroundDispatcher, requires = emptySet())
        // 注册 ToolSource，App.kt 里 MultiToolRegistry 聚合时 getAll(ToolSource::class) 就能拿到
        ctx.services.provide(ToolSource::class, toolSource, requires = emptySet())
        ctx.services.provide(ToolSource::class, deviceTools, requires = emptySet())
    }
}

