package io.github.aixtin.nyrallite.plugins.wasm

import android.content.Context
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WASM 插件工具来源：把已安装且带工具元数据的 .wasm 插件注册成模型可调用的工具。
 *
 * 与 MCP 远程工具、系统原生工具平级，由 MultiToolRegistry 聚合；
 * 调用统一过 GateBus 门禁（外层管模型能不能调），插件内部再过能力网关（内层管插件能碰什么）。
 *
 * 工具名格式：wasm_<插件id>_<工具名>。模型 API 对工具名字符有白名单
 * （字母数字下划线短横线），冒号和点号都会被整批请求 400 拒绝，所以只用下划线连接；
 * 插件 id 与工具名都可能含下划线，名字不靠分隔符还原——list() 时把
 * 名字到 (插件id, 工具名) 的对应关系登记进 [registry]，调用时查表分发。
 * 模型编造的名字查无登记，owns() 直接放行走「工具未找到」的优雅失败。
 *
 * 元数据解析结果按文件哈希缓存，文件被更新（重新导入触发重新确认）后哈希变化自动失效。
 */
class WasmToolSource(
    private val appContext: Context,
    private val notifier: ((title: String, body: String) -> Boolean)? = null,
    private val httpFetcher: ((url: String) -> ByteArray?)? = null,
) : ToolSource {

    override val sourceId: String = "builtin:wasm-plugins"

    /** 插件 id → (文件哈希, 解析出的工具声明)；哈希不符即重解析 */
    private val cache = ConcurrentHashMap<String, Pair<String, List<ToolSpec>>>()

    /** 模型侧工具名 → 插件 id；list() 全量重建，调用时据此找到插件 */
    private val registry = ConcurrentHashMap<String, String>()

    override suspend fun list(): List<ToolSpec> = withContext(Dispatchers.IO) {
        val specs = ArrayList<ToolSpec>()
        val registered = HashMap<String, String>()
        for (pluginId in WasmPluginRepo.list(appContext)) {
            val bytes = runCatching { WasmPluginRepo.file(appContext, pluginId).readBytes() }.getOrNull() ?: continue
            val sha = WasmPluginRuntime.sha256(bytes)
            val cached = cache[pluginId]
            val toolSpecs = if (cached?.first?.equals(sha, ignoreCase = true) == true) {
                cached.second
            } else {
                val parsed = when (val m = parseWasmPluginMeta(bytes)) {
                    is WasmPluginMetaResult.Ok -> listOf(
                        ToolSpec(
                            name = toolName(pluginId, m.meta.toolName),
                            description = m.meta.description,
                            parametersJson = m.meta.parametersJson,
                        ),
                    )
                    is WasmPluginMetaResult.None -> emptyList()
                    is WasmPluginMetaResult.Fail -> emptyList()
                }
                cache[pluginId] = Pair(sha, parsed)
                parsed
            }
            for (spec in toolSpecs) {
                // 清洗后撞名的插件先到先得，与 MultiToolRegistry 的去重语义一致
                if (registered.put(spec.name, pluginId) == null) specs += spec
            }
        }
        registry.clear()
        registry.putAll(registered)
        specs
    }

    override suspend fun owns(name: String): Boolean = registry.containsKey(name)

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        val pluginId = registry[name] ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { WasmPluginRepo.file(appContext, pluginId).readBytes() }.getOrNull()
                ?: return@withContext ToolResult("插件 $pluginId 未安装或已被移除", isError = true)

            // 按当前文件重新生成工具名并比对：文件被同名插件覆盖而信任未更新时在此拦截
            val meta = when (val m = parseWasmPluginMeta(bytes)) {
                is WasmPluginMetaResult.Ok -> m.meta
                is WasmPluginMetaResult.Fail -> return@withContext ToolResult("插件 $pluginId 元数据校验失败：${m.reason}", isError = true)
                is WasmPluginMetaResult.None -> return@withContext ToolResult("插件 $pluginId 没有工具元数据", isError = true)
            }
            if (toolName(pluginId, meta.toolName) != name) {
                return@withContext ToolResult("插件 $pluginId 当前不提供工具 $name，可用工具：${meta.toolName}", isError = true)
            }

            val gateway = WasmPluginGateway(
                notifier = notifier,
                httpFetcher = httpFetcher,
                argsProvider = { argumentsJson.encodeToByteArray() },
            )
            WasmToolExecutor.call(
                pluginId = pluginId,
                wasmBytes = bytes,
                trustedSha256 = WasmPluginStore(appContext).get(pluginId),
                argumentsJson = argumentsJson,
                gateway = gateway,
            )
        }
    }

    companion object {
        const val TOOL_PREFIX = "wasm_"

        /** 模型 API 对工具名的长度上限 */
        const val MAX_TOOL_NAME = 64

        private val NOT_ALLOWED = Regex("[^a-zA-Z0-9_-]")

        /** 插件 id 清洗后拼上工具名；超长时截断插件 id 并追加短哈希防碰撞 */
        fun toolName(pluginId: String, tool: String): String {
            val clean = pluginId.replace(NOT_ALLOWED, "_")
            val base = "$TOOL_PREFIX${clean}_$tool"
            if (base.length <= MAX_TOOL_NAME) return base
            val hash = WasmPluginRuntime.sha256(pluginId.encodeToByteArray()).take(8)
            val keep = MAX_TOOL_NAME - TOOL_PREFIX.length - 1 - hash.length - 1 - tool.length
            return "$TOOL_PREFIX${clean.take(keep.coerceAtLeast(0))}_${hash}_$tool"
        }
    }
}
