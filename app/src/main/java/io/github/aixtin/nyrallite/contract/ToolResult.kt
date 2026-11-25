package io.github.aixtin.nyrallite.contract

/** 工具执行结果：[content] 回填给模型，[isError] 供界面标记失败。 */
data class ToolResult(
    val content: String,
    val isError: Boolean = false,
)
