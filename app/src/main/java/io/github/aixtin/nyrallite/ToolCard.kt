package io.github.aixtin.nyrallite

/**
 * 工具调用卡片：一行摘要 + 可展开的参数/结果。
 * running 为 true 时只显示"运行中"，结束后由执行回调补齐结果与耗时。
 */
data class ToolCard(
    val name: String,
    val arguments: String,
    var result: String = "",
    var ok: Boolean = true,
    var running: Boolean = true,
    var durationMs: Long = 0L,
    var expanded: Boolean = false,
)
