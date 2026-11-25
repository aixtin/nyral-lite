package io.github.aixtin.nyrallite.plugins.modelopenai

import io.github.aixtin.nyrallite.contract.ChatEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 单帧解析：只认标准字段 choices[0].delta.content + reasoning_content / reasoning。
 *
 * 注意：不能因为 finish_reason 非空就整帧丢弃——个别端点最后一帧同时带正文和
 * finish_reason，丢帧会表现为"话说一半"。空 delta 自然返回 null。
 */
internal object OpenAiDeltaParser {

    fun parse(data: String): ChatEvent.Delta? {
        return try {
            val choices = JSONObject(data).optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
            val content = extractText(delta.opt("content"))
            val reasoning = extractText(delta.opt("reasoning_content"))
                .ifEmpty { extractText(delta.opt("reasoning")) }
            if (content.isEmpty() && reasoning.isEmpty()) {
                null
            } else {
                ChatEvent.Delta(content, reasoning)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** content/reasoning 既可能是字符串，也可能是 [{type:"text",text:"..."}] 数组。 */
    private fun extractText(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        if (value is String) return value
        if (value is JSONArray) {
            val sb = StringBuilder()
            for (i in 0 until value.length()) {
                val part = value.optJSONObject(i) ?: continue
                if (part.optString("type") == "text") sb.append(part.optString("text"))
            }
            return sb.toString()
        }
        return value.toString()
    }
}
