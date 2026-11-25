package io.github.aixtin.nyrallite.contract

/**
 * 工具注册表：模型侧工具能力对主体的唯一入口。
 *
 * 实现方（mcp-client 插件）自行处理网络与协议；[list] 返回空表示当前没有可用工具，
 * 主体据此不向模型声明 tools。两个方法内部自行切 IO 线程，调用方可以直接在主线程 await。
 */
interface ToolRegistry {

    /** 当前可声明的工具清单；短时间内的重复调用应由实现方缓存。 */
    suspend fun list(): List<ToolSpec>

    /** 执行一次工具调用；业务失败请返回 [ToolResult.isError] 而不是抛异常。 */
    suspend fun call(name: String, argumentsJson: String): ToolResult
}
