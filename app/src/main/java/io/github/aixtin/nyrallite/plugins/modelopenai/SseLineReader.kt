package io.github.aixtin.nyrallite.plugins.modelopenai

/** SSE 行解析：只认 `data:` 负载，空行与非 data 行一律跳过。 */
internal object SseLineReader {

    fun isDone(line: String): Boolean =
        line.startsWith("data:") && line.removePrefix("data:").trim() == "[DONE]"

    /** 返回 data 负载；不是 data 行或负载为空时返回 null。 */
    fun payload(line: String): String? {
        if (!line.startsWith("data:")) return null
        return line.removePrefix("data:").trim().ifEmpty { null }
    }
}
