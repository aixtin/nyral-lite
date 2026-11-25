package io.github.aixtin.nyrallite.plugins.modelopenai

import org.json.JSONObject

/** 从错误响应体里提取人话 message；没有就返回截断原文。 */
internal object OpenAiErrorMessages {

    fun extract(body: String): String {
        return try {
            val msg = JSONObject(body).optJSONObject("error")?.optString("message")
            if (!msg.isNullOrBlank()) msg else body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
