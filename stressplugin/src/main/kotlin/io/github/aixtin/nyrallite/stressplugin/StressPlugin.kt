package io.github.aixtin.nyrallite.stressplugin

import android.util.Log
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.kernel.ConfigurablePlugin
import io.github.aixtin.nyrallite.kernel.GateResult
import io.github.aixtin.nyrallite.kernel.PanelField
import io.github.aixtin.nyrallite.kernel.PanelSpec
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import org.json.JSONObject

/**
 * 真实复杂度压力插件：120 个工具类 + 4 个工具，验证 DexClassLoader
 * 对非平凡插件的装载与执行。装载耗时以 logcat 时间戳为准。
 */
class StressPlugin : Plugin, ConfigurablePlugin {
    override val manifest = PluginManifest(
        id = "stress-plugin",
        version = "0.1.0",
        builtin = false,
    )

    override fun install(ctx: PluginContext) {
        Log.i("NyralDexTest", "✅ StressPlugin installed (120 util classes)")
        ctx.services.provide(ToolSource::class, StressTools(ctx))
    }

    override fun panel(): PanelSpec = PanelSpec(
        title = "压力插件配置",
        fields = listOf(
            PanelField.Text(key = "corpus", label = "默认语料", placeholder = "默认文本"),
            PanelField.Switch(key = "verbose", label = "详细日志", default = false),
        ),
    )
}

private class StressTools(private val ctx: PluginContext) : ToolSource {

    private val specs = listOf(
        ToolSpec(
            name = "stress_text_stats",
            description = "统计文本：字符数、词数、词频 Top3、120 类联合指纹。",
            parametersJson = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}""",
        ),
        ToolSpec(
            name = "stress_sort_bench",
            description = "排序基准：生成 N 个随机串排序并返回耗时与校验值。",
            parametersJson = """{"type":"object","properties":{"n":{"type":"integer"}},"required":["n"],"additionalProperties":false}""",
        ),
        ToolSpec(
            name = "stress_matrix",
            description = "矩阵遍历：N 阶矩阵对角线求和，跨 120 个工具类分发。",
            parametersJson = """{"type":"object","properties":{"n":{"type":"integer"}},"required":["n"],"additionalProperties":false}""",
        ),
        ToolSpec(
            name = "stress_config_echo",
            description = "回读配置面板的 corpus 与 verbose。",
            parametersJson = """{"type":"object","properties":{},"additionalProperties":false}""",
        ),
    )

    private val utils: List<StressOps> by lazy { StressRegistry.allUtils() }

    override suspend fun list(): List<ToolSpec> = specs

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        val spec = specs.firstOrNull { it.name == name } ?: return null
        when (val gate = ctx.gates.check(ToolCallRequest(ctx.pluginId, name, argumentsJson))) {
            is GateResult.Block -> return ToolResult("安全策略拦截：${gate.reason}", isError = true)
            GateResult.Allow -> Unit
        }
        val args = JSONObject(argumentsJson.ifBlank { "{}" })
        return try {
            ToolResult(execute(name, args))
        } catch (t: Throwable) {
            ToolResult("执行失败：${t.message}", isError = true)
        }
    }

    private fun execute(name: String, args: JSONObject): String = when (name) {
        "stress_text_stats" -> {
            val text = args.optString("text", "")
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val top = words.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(3)
                .joinToString("、") { "${it.key}×${it.value}" }
            val fingerprint = utils.fold(0L) { acc, u -> (acc + u.fold(words.map { it.length })) % 1_000_000_007 }
            "字符 ${text.length}，词 ${words.size}，Top3：$top，指纹 $fingerprint"
        }
        "stress_sort_bench" -> {
            val n = args.optInt("n", 1000).coerceIn(10, 100_000)
            val start = System.nanoTime()
            val items = List(n) { "item-" + ((it * 7919L) % n).toString().padStart(6, '0') + "-" + utils[it % 120].transform("x") }
            val sorted = items.sorted()
            val cost = (System.nanoTime() - start) / 1_000_000.0
            "排序 $n 项耗时 ${"%.2f".format(cost)}ms，首项 ${sorted.first()}，校验 ${utils.size} 类参与"
        }
        "stress_matrix" -> {
            val n = args.optInt("n", 64).coerceIn(2, 2048)
            val start = System.nanoTime()
            val trace = utils.fold(0L) { acc, u -> acc + u.matrix(n / 8) }
            val cost = (System.nanoTime() - start) / 1_000_000.0
            "120 类 × ${n / 8} 阶矩阵遍历完成，累加 $trace，耗时 ${"%.2f".format(cost)}ms"
        }
        "stress_config_echo" -> {
            val corpus = ctx.config.getString("corpus", "").ifBlank { "未配置" }
            val verbose = ctx.config.getString("verbose", "false")
            "corpus=$corpus, verbose=$verbose（配置面板连通正常）"
        }
        else -> "未知工具"
    }
}
