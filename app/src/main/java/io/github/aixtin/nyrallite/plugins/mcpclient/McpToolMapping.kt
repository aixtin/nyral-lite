package io.github.aixtin.nyrallite.plugins.mcpclient

import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** MCP 协议数据到契约模型的转换：tools/list 的 schema、tools/call 的 content。 */
internal object McpToolMapping {

    fun specsOf(tools: JSONArray): List<ToolSpec> {
        val specs = ArrayList<ToolSpec>(tools.length())
        for (i in 0 until tools.length()) {
            val tool = tools.optJSONObject(i) ?: continue
            val name = tool.optString("name")
            if (name.isBlank()) continue
            val schema = tool.optJSONObject("inputSchema") ?: JSONObject()
            specs.add(ToolSpec(name, tool.optString("description"), schema.toString()))
        }
        return specs
    }

    /** 把 content 数组拼成纯文本；非文本块用类型占位，避免静默丢弃。 */
    fun resultOf(response: JSONObject): ToolResult {
        val blocks = response.optJSONArray("content")
        val text = StringBuilder()
        if (blocks == null) {
            text.append(response.toString())
        } else {
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                if (text.isNotEmpty()) text.append('\n')
                if (block.optString("type") == "text") {
                    text.append(block.optString("text"))
                } else {
                    text.append('[').append(block.optString("type", "content")).append(']')
                }
            }
        }
        return ToolResult(
            content = text.toString().ifBlank { "（工具无输出）" },
            isError = response.optBoolean("isError", false),
        )
    }
}
