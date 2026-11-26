package io.github.aixtin.nyrallite.plugins.modelopenai

import io.github.aixtin.nyrallite.contract.ApiConfig
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatRequest
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.ToolCall
import io.github.aixtin.nyrallite.contract.ToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI 兼容请求体。厂商相关参数全部收在这里，不出插件。 */
internal object OpenAiRequestBody {

    fun build(
        config: ApiConfig,
        request: ChatRequest,
        openAttachment: (AttachmentRef) -> ByteArray?,
    ): String {
        val messages = JSONArray()
        if (request.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", request.systemPrompt))
        }
        for (turn in request.turns) messages.put(messageOf(turn, openAttachment))
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", 0.7)
        if (request.tools.isNotEmpty()) {
            body.put("tools", toolsOf(request.tools))
            body.put("tool_choice", "auto")
        }
        return body.toString()
    }

    private fun messageOf(
        turn: ChatTurn,
        openAttachment: (AttachmentRef) -> ByteArray?,
    ): JSONObject {
        val message = JSONObject().put("role", turn.role)
        if (turn.role == ChatTurn.ROLE_TOOL) {
            return message.put("content", turn.content).put("tool_call_id", turn.toolCallId)
        }
        if (turn.toolCalls.isNotEmpty()) {
            // 纯工具调用轮没有正文，content 必须是 null 而不是空串
            message.put("content", if (turn.content.isEmpty()) JSONObject.NULL else turn.content)
            return message.put("tool_calls", JSONArray().apply {
                for (call in turn.toolCalls) put(toolCallOf(call))
            })
        }
        return message.put(
            "content",
            ContentParts.contentOf(turn.content, turn.attachments, openAttachment),
        )
    }

    private fun toolCallOf(call: ToolCall): JSONObject = JSONObject()
        .put("id", call.id)
        .put("type", "function")
        .put(
            "function",
            JSONObject().put("name", call.name).put("arguments", call.arguments),
        )

    private fun toolsOf(tools: List<ToolSpec>): JSONArray = JSONArray().apply {
        for (tool in tools) {
            put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", schemaOf(tool.parametersJson)),
                    ),
            )
        }
    }

    /** 参数 Schema 原样透传；模型端点要求是对象，解析失败时退化成空对象。 */
    private fun schemaOf(json: String): JSONObject =
        runCatching { JSONObject(json) }.getOrElse {
            JSONObject().put("type", "object").put("properties", JSONObject())
        }
}
