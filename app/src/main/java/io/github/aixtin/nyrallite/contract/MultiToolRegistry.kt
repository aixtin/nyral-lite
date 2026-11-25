package io.github.aixtin.nyrallite.contract

/**
 * 多来源聚合工具注册表：抹平 MCP 远程工具、系统原生工具、以后 WebView 工具的差异，
 * 对主体暴露单一个 [ToolRegistry] 接口。
 *
 * list() 合并所有来源的工具并按 name 去重（先注册的来源优先）；
 * call() 扫描所有来源，找到能执行的那个就委托过去。
 *
 * 来源的"热加载"：ServiceRegistry.get<ToolSource>() 每次都查 Map，
 * 新注册的来源下次 list() 自动包含，uninstall 后的来源自动排除。
 */
class MultiToolRegistry(
    private val sources: () -> List<ToolSource>,
) : ToolRegistry {

    override suspend fun list(): List<ToolSpec> {
        val all = ArrayList<ToolSpec>()
        val seen = HashSet<String>()
        for (source in sources()) {
            // 单个来源（如远程 MCP 断网）失败不能拖垮其它来源的工具，这里逐个隔离
            val tools = try {
                source.list()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            for (tool in tools) {
                if (seen.add(tool.name)) all.add(tool)
            }
        }
        return all
    }

    override suspend fun call(name: String, argumentsJson: String): ToolResult {
        for (source in sources()) {
            val result = source.callOrNull(name, argumentsJson)
            if (result != null) return result
        }
        return ToolResult(
            content = "工具未找到: $name，当前来源中没有名为该的工具",
            isError = true,
        )
    }
}
