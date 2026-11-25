package io.github.aixtin.nyrallite.plugins.system

import org.json.JSONObject

/** 安全地把工具调用参数 JSON 解析成 String→String Map。 */
internal fun parseToolArgs(json: String): Map<String, String?> {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
    val map = LinkedHashMap<String, String?>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val v = obj.opt(k)
        map[k] = when (v) {
            null, JSONObject.NULL -> null
            else -> v.toString()
        }
    }
    return map
}
