package io.github.aixtin.nyrallite.contract

/**
 * 一个可调用工具的描述。
 * 参数是用 JSON Schema 字符串表示的，契约层不依赖 org.json。
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)
