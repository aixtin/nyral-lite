package io.github.aixtin.nyrallite.contract

/**
 * 工具来源：注册一组工具 + 执行调用。
 * 插件实现它并通过 ctx.services.provide(ToolSource::class, ...) 注册，
 * 宿主会把所有来源聚合起来交给模型。
 */
interface ToolSource {

    /** 来源标识：门禁判定与审计日志按此归属。默认按类名生成，可与宿主接口各自演进。 */
    val sourceId: String get() = "plugin:${javaClass.name}"

    /** 这个来源当前可声明的工具清单。 */
    suspend fun list(): List<ToolSpec>

    /** 执行一次工具调用；只处理自己来源的工具名，其他名字返回 null。 */
    suspend fun callOrNull(name: String, argumentsJson: String): ToolResult?
}
