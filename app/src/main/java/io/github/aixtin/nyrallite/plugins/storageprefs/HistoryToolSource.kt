package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.Message
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import org.json.JSONObject

/**
 * 历史检索工具：给模型一个「摘要里提到了，但原文细节在本地」时的回读入口。
 *
 * 只读当前会话文件，不跨会话，避免把别人设的台词带进当前上下文。
 */
class HistoryToolSource(
    private val sessions: SessionStore,
    private val sessionId: () -> String = { "" },
) : ToolSource {

    override val sourceId: String = "builtin:history"

    override suspend fun list(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "history_search",
            description = "检索当前会话的历史原文。当摘要或上下文不足以确认具体措辞、数字、名单、约定时使用。" +
                "返回命中消息在会话中的序号、角色和原文片段；序号可用于对照上下文。",
            parametersJson = PARAMS,
        ),
    )

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        if (name != "history_search") return null
        val obj = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return ToolResult("参数不是合法 JSON", isError = true)
        val query = obj.optString("query").trim()
        if (query.isEmpty()) return ToolResult("缺少参数 query", isError = true)
        val limit = obj.optInt("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val id = sessionId()
        val session = id.takeIf { it.isNotBlank() }
            ?.let { runCatching { sessions.load(it) }.getOrNull() }
            ?: return ToolResult("当前会话还没有可检索的历史。", isError = true)
        val hits = HistorySearch.rank(query, session.messages, limit)
        if (hits.isEmpty()) {
            return ToolResult(
                "当前会话共 ${session.messages.size} 条消息，没有命中「$query」。换更短或更具体的关键词再试。",
            )
        }
        return ToolResult(format(hits, session.messages.size))
    }

    private fun format(hits: List<HistorySearch.Hit>, total: Int): String {
        return buildString {
            append("命中 ").append(hits.size).append(" 条（会话共 ").append(total).append(" 条，序号为会话内位置）：\n")
            hits.forEach { hit ->
                val who = if (hit.role == Message.ROLE_USER) "用户" else "助手"
                append("第 ").append(hit.index).append(" 条 [").append(who).append("] ")
                append(hit.content.take(MAX_CHARS))
                if (hit.content.length > MAX_CHARS) append("…（已截断）")
                append('\n')
            }
        }.trim()
    }

    private companion object {
        const val DEFAULT_LIMIT = 3
        const val MAX_LIMIT = 5
        const val MAX_CHARS = 300

        const val PARAMS = """{
            "type":"object",
            "properties":{
                "query":{"type":"string","description":"要回读的主题词、人名或短句"},
                "limit":{"type":"integer","description":"最多返回几条，默认 3，最多 5"}
            },
            "required":["query"]
        }"""
    }
}
