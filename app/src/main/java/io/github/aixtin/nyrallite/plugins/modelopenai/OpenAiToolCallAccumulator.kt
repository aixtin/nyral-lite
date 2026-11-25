package io.github.aixtin.nyrallite.plugins.modelopenai

import io.github.aixtin.nyrallite.contract.ToolCall
import org.json.JSONObject
import java.util.TreeMap

/**
 * 工具调用分片累加器。
 *
 * 流式协议里 tool_calls 按 index 分片下发：首片带 id/name，后续片只追加
 * function.arguments 的字符串片段。这里按 index 归并，流结束时一次性交付。
 */
internal class OpenAiToolCallAccumulator {

    private class Partial {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private val parts = TreeMap<Int, Partial>()

    fun accept(data: String) {
        val choices = try {
            JSONObject(data).optJSONArray("choices") ?: return
        } catch (_: Exception) {
            return
        }
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return
        val calls = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until calls.length()) {
            val item = calls.optJSONObject(i) ?: continue
            val part = parts.getOrPut(item.optInt("index", i)) { Partial() }
            if (item.has("id")) part.id = item.optString("id")
            val function = item.optJSONObject("function") ?: continue
            if (function.has("name")) part.name = function.optString("name")
            part.arguments.append(function.optString("arguments"))
        }
    }

    /** 取出已完成汇总的调用；没有名字的残片丢弃。 */
    fun take(): List<ToolCall> = parts.values
        .filter { it.name.isNotBlank() }
        .map { part ->
            ToolCall(
                id = part.id.ifBlank { "call_" + System.nanoTime() },
                name = part.name,
                arguments = part.arguments.toString().ifBlank { "{}" },
            )
        }
}
