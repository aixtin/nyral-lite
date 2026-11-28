package io.github.aixtin.nyrallite.plugins.js

import io.github.aixtin.nyrallite.plugins.wasm.isStrictJson

/**
 * JS 工具元数据：从脚本头部的 nyral.tool 块注释（内嵌 JSON）解析，安装期硬校验。
 *
 * 与 WasmPluginMeta 同一心智：manifest 是作者写的，一律视为不可信输入；
 * JSON 结构、工具名、能力名、契约版本全部校验，不合格拒绝安装。
 * 能力声明是插座清单：未声明的能力在网关层面不存在，声明了宿主没有的能力同样拒绝——
 * 装了也跑不起来的声明只会误导安装确认。
 */
data class JsToolMeta(
    val name: String,
    val description: String,
    val contractVersion: String,
    val capabilities: List<String>,
) {
    companion object {
        /** 承载元数据的注释块标记 */
        const val MARKER = "nyral.tool"

        /** 工具名：1-64 个任意非空白字符（展示用，与 WASM 的模型侧工具名不同） */
        const val MAX_NAME = 64

        const val MAX_DESCRIPTION = 500

        /** 能力名：小写字母+下划线，最长 32 字符 */
        val CAPABILITY_REGEX = Regex("^[a-z][a-z0-9_]{0,31}$")

        /** 契约版本格式：MAJOR.MINOR */
        val CONTRACT_REGEX = Regex("^[0-9]+\\.[0-9]+$")

        /** M2 宿主已实现的能力清单；随批次扩展 */
        val KNOWN_CAPABILITIES = setOf("http_fetch")
    }
}

/** 元数据解析结果：Ok 携带元数据；Fail 携带拒绝原因 */
sealed interface JsToolMetaResult {
    data class Ok(val meta: JsToolMeta) : JsToolMetaResult
    data class Fail(val reason: String) : JsToolMetaResult
}

/**
 * 解析并校验 JS 工具元数据：取脚本第一个块注释，须以 nyral.tool 标记开头。
 * 契约版本只拦大版本（与 Dex 插件同口径）：大版本不匹配直接拒绝，小版本仅记录。
 */
fun parseJsToolMeta(script: String): JsToolMetaResult {
    val block = Regex("/\\*\\s*(nyral\\.tool)\\s*(\\{.*?\\})\\s*\\*/", RegexOption.DOT_MATCHES_ALL)
        .find(script) ?: return JsToolMetaResult.Fail("脚本头部缺少 nyral.tool 元数据注释块")

    val raw = block.groupValues[2]
    // org.json 对无引号字符串、单引号等过于宽容，不可信输入先过严格 JSON 校验
    if (!isStrictJson(raw)) {
        return JsToolMetaResult.Fail("元数据不是合法 JSON")
    }
    val json = runCatching { org.json.JSONObject(raw) }.getOrElse {
        return JsToolMetaResult.Fail("元数据不是合法 JSON")
    }

    val name = json.optString("name")
    if (name.isBlank() || name.length > JsToolMeta.MAX_NAME) {
        return JsToolMetaResult.Fail("工具名缺失或超过 ${JsToolMeta.MAX_NAME} 字符")
    }

    val description = json.optString("description")
    if (description.isBlank() || description.length > JsToolMeta.MAX_DESCRIPTION) {
        return JsToolMetaResult.Fail("工具描述缺失或超过 ${JsToolMeta.MAX_DESCRIPTION} 字符")
    }

    val contract = json.optString("contractVersion")
    if (!JsToolMeta.CONTRACT_REGEX.matches(contract)) {
        return JsToolMetaResult.Fail("契约版本格式不合法：$contract")
    }
    val major = contract.substringBefore(".").toIntOrNull()
        ?: return JsToolMetaResult.Fail("契约版本格式不合法：$contract")
    if (major != io.github.aixtin.nyrallite.contract.ContractVersion.MAJOR) {
        return JsToolMetaResult.Fail(
            "契约大版本不兼容：插件=$contract，宿主=" +
                "${io.github.aixtin.nyrallite.contract.ContractVersion.MAJOR}." +
                "${io.github.aixtin.nyrallite.contract.ContractVersion.MINOR}",
        )
    }

    val capsJson = json.optJSONArray("capabilities") ?: org.json.JSONArray()
    val capabilities = mutableListOf<String>()
    for (i in 0 until capsJson.length()) {
        val cap = capsJson.optString(i)
        if (!JsToolMeta.CAPABILITY_REGEX.matches(cap)) {
            return JsToolMetaResult.Fail("能力名不合法：$cap")
        }
        if (cap !in JsToolMeta.KNOWN_CAPABILITIES) {
            return JsToolMetaResult.Fail("宿主未实现的能力：$cap")
        }
        if (cap !in capabilities) capabilities += cap
    }

    return JsToolMetaResult.Ok(JsToolMeta(name, description, contract, capabilities))
}
