package io.github.aixtin.nyrallite.contract

/**
 * 工具来源：注册一组工具 + 执行调用。
 *
 * 与 [ToolRegistry] 的关系：ToolRegistry 是主体认的统一入口，MultiToolRegistry 聚合多个 ToolSource；
 * 每个插件可以注册自己的 ToolSource 实现（MCP 远程工具、系统原生工具、以后的 WebView 工具……），
 * 主体用 MultiToolRegistry 抹平差异，对 ChatTransport 暴露单一个 ToolRegistry 接口。
 *
 * 插件只关心自己的来源能不能被聚合，不关心有多少个来源、谁先谁后——Kernel 负责组装。
 */
interface ToolSource {

    /** 这个来源当前可声明的工具清单。 */
    suspend fun list(): List<ToolSpec>

    /** 执行一次工具调用；只处理自己来源的工具名，其他名字返回 null。 */
    suspend fun callOrNull(name: String, argumentsJson: String): ToolResult?
}
