package io.github.aixtin.nyrallite.testplugin

import android.util.Log
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.kernel.ConfigurablePlugin
import io.github.aixtin.nyrallite.kernel.GateResult
import io.github.aixtin.nyrallite.kernel.PanelSpec
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest

/**
 * 最小测试插件：验证 DexClassLoader 热加载管线，并向模型提供一个 hello_ping 工具。
 * 同时用来验证「禁用插件后工具消失」「同 id 重新导入即升级」与「声明式配置面板」。
 */
class HelloPlugin : Plugin, ConfigurablePlugin {
    override val manifest = PluginManifest(
        id = "hello-test-plugin",
        version = "0.0.1",
        builtin = false,
    )

    override fun install(ctx: PluginContext) {
        Log.i("NyralDexTest", "✅ HelloPlugin installed via DexClassLoader!")
        ctx.services.provide(ToolSource::class, HelloTools(ctx))
    }

    override fun panel(): PanelSpec = helloPanel()
}

/** 一个不依赖任何宿主能力的连通性工具：返回 pong，并带上面板里配置的值。 */
private class HelloTools(private val ctx: PluginContext) : ToolSource {

    private val spec = ToolSpec(
        name = "hello_ping",
        description = "连通性测试：返回 pong 与当前配置（语言、详细日志、API 地址是否配置），不访问网络。",
        parametersJson = """{"type":"object","properties":{},"additionalProperties":false}""",
    )

    override suspend fun list(): List<ToolSpec> = listOf(spec)

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        if (name != spec.name) return null
        when (val gate = ctx.gates.check(ToolCallRequest(ctx.pluginId, name, argumentsJson))) {
            is GateResult.Block -> return ToolResult("安全策略拦截：${gate.reason}", isError = true)
            GateResult.Allow -> Unit
        }
        // 每次调用时重新读配置：面板保存后无需重启，下一次调用即生效
        val lang = ctx.config.getString("lang", "zh")
        val verbose = ctx.config.getString("verbose", "false")
        val api = ctx.config.getString("api_base", "").ifBlank { "未配置" }
        return ToolResult("pong（lang=$lang, verbose=$verbose, api=$api）")
    }
}
