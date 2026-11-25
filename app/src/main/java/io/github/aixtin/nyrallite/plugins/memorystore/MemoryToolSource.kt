package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryEntry
import io.github.aixtin.nyrallite.contract.MemoryStore
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆工具：目录常驻上下文（见 MemoryIndex），正文靠这里按需取。
 * 三个动作都只动本地文件，不触网、不申请任何系统权限。
 */
class MemoryToolSource(
    private val store: MemoryStore,
    private val sessionId: () -> String = { "" },
) : ToolSource {

    override suspend fun list(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "remember",
            description = "把需要长期记住的信息写入记忆。仅当用户明确要求记住，或表达了跨会话的长期偏好/设定时使用；" +
                "不要用它记录普通聊天内容。text 写成一句自包含的结论，title 是可选的短标题。",
            parametersJson = PARAMS_REMEMBER,
        ),
        ToolSpec(
            name = "memory_search",
            description = "检索长期记忆的正文细节。当用户提到以前说过的事、你的偏好或历史决定，" +
                "而上下文里只有记忆索引标题时使用。返回按相关度排序的条目（含编号与时间）。",
            parametersJson = PARAMS_SEARCH,
        ),
        ToolSpec(
            name = "forget",
            description = "按编号删除一条长期记忆。仅在用户明确要求忘掉某事时使用。",
            parametersJson = PARAMS_FORGET,
        ),
    )

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? =
        when (name) {
            "remember" -> remember(argumentsJson)
            "memory_search" -> search(argumentsJson)
            "forget" -> forget(argumentsJson)
            else -> null
        }

    private fun remember(json: String): ToolResult {
        val obj = parse(json) ?: return ToolResult("参数不是合法 JSON", isError = true)
        val text = obj.optString("text").trim()
        if (text.isEmpty()) return ToolResult("缺少参数 text", isError = true)
        val entry = store.add(
            text = text,
            title = obj.optString("title"),
            sessionId = sessionId(),
            pinned = obj.optBoolean("pinned", false),
        )
        return ToolResult("已记住 #${entry.id}「${entry.title}」，当前共 ${store.count()} 条。")
    }

    private fun search(json: String): ToolResult {
        val obj = parse(json) ?: return ToolResult("参数不是合法 JSON", isError = true)
        val query = obj.optString("query").trim()
        if (query.isEmpty()) return ToolResult("缺少参数 query", isError = true)
        val hits = store.search(query, MAX_HITS)
        if (hits.isNotEmpty()) return ToolResult(format(hits, "相关记忆（按相关度排序，编号可用于 forget）："))
        // 关键词没命中不等于没记忆：中英文提问/记忆语言不一致时必然走到这里，
        // 至少把置顶条目（用户明确要求长期记住的）交出去，别让模型回答"没存过"。
        val pinned = store.all().filter { it.pinned }.sortedByDescending { it.ts }.take(MAX_FALLBACK)
        if (pinned.isEmpty()) return ToolResult("记忆中没有与该主题相关的内容。")
        return ToolResult(format(pinned, "没有直接命中该主题，以下是用户置顶的长期记忆："))
    }

    private fun format(entries: List<MemoryEntry>, header: String): String {
        val dates = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return buildString {
            append(header).append('\n')
            entries.forEachIndexed { index, entry ->
                append(index + 1).append(". #").append(entry.id)
                    .append(" [").append(dates.format(Date(entry.ts))).append("] ")
                append(entry.text.take(MAX_TEXT_CHARS))
                if (entry.text.length > MAX_TEXT_CHARS) append("…（已截断）")
                append('\n')
            }
        }.trim()
    }

    private fun forget(json: String): ToolResult {
        val obj = parse(json) ?: return ToolResult("参数不是合法 JSON", isError = true)
        val id = obj.optLong("id", -1L)
        if (id <= 0) return ToolResult("缺少参数 id", isError = true)
        return if (store.delete(id)) {
            ToolResult("已删除记忆 #$id，当前共 ${store.count()} 条。")
        } else {
            ToolResult("没有找到 #$id 这条记忆。", isError = true)
        }
    }

    private fun parse(json: String): JSONObject? =
        runCatching { JSONObject(json) }.getOrNull()

    private companion object {
        const val MAX_HITS = 5
        const val MAX_FALLBACK = 3
        const val MAX_TEXT_CHARS = 400

        const val PARAMS_REMEMBER = """{
            "type":"object",
            "properties":{
                "text":{"type":"string","description":"要长期记住的一条结论，自包含，不要写代词指代"},
                "title":{"type":"string","description":"可选短标题，不超过 15 字"},
                "pinned":{"type":"boolean","description":"是否置顶（高频重要的用户设定）"}
            },
            "required":["text"]
        }"""

        const val PARAMS_SEARCH = """{
            "type":"object",
            "properties":{
                "query":{"type":"string","description":"用于检索的主题词或短句"}
            },
            "required":["query"]
        }"""

        const val PARAMS_FORGET = """{
            "type":"object",
            "properties":{
                "id":{"type":"integer","description":"记忆编号，来自记忆索引或检索结果"}
            },
            "required":["id"]
        }"""
    }
}
