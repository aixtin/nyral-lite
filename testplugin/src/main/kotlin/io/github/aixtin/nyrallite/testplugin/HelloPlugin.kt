package io.github.aixtin.nyrallite.testplugin

import android.util.Log
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.kernel.GateResult
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest

/**
 * 最小测试插件：验证 DexClassLoader 热加载管线，并向模型提供一个 hello_ping 工具。
 * 同时用来验证「禁用插件后工具消失」与「同 id 重新导入即升级」。
 */
class HelloPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "hello-test-plugin",
        version = "0.0.1",
        builtin = false,
    )

    override fun install(ctx: PluginContext) {
        Log.i("NyralDexTest", "✅ HelloPlugin installed via DexClassLoader!")
        ctx.services.provide(ToolSource::class, HelloTools(ctx))
    }
}

/** 一个不依赖任何宿主能力的连通性工具：调用返回 pong。 */
private class HelloTools(private val ctx: PluginContext) : ToolSource {

    private val spec = ToolSpec(
        name = "hello_ping",
        description = "连通性测试：直接返回 pong，不访问网络也不修改任何数据。",
        parametersJson = """{"type":"object","properties":{},"additionalProperties":false}""",
    )

    override suspend fun list(): List<ToolSpec> = listOf(spec)

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        if (name != spec.name) return null
        when (val gate = ctx.gates.check(ToolCallRequest(ctx.pluginId, name, argumentsJson))) {
            is GateResult.Block -> return ToolResult("安全策略拦截：${gate.reason}", isError = true)
            GateResult.Allow -> Unit
        }
        return ToolResult("pong")
    }
}
