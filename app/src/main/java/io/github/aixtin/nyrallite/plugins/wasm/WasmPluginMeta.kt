package io.github.aixtin.nyrallite.plugins.wasm

import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import org.json.JSONObject

/**
 * WASM 插件的工具元数据：从自定义段 [WasmPluginMeta.SECTION_NAME] 解析，安装期硬校验。
 *
 * 自定义段内容是插件作者写的，一律视为不可信输入：JSON 结构、工具名格式、
 * 参数 schema、入口导出全部校验，不合格拒绝安装。通过校验后随文件哈希进入
 * 信任登记，装后不可篡改。
 *
 * 模型侧工具清单由宿主注册表生成（WasmToolSource），模型只能调用已注册的工具；
 * 编造的工具名在分发层查无此名，返回明确错误后模型自行纠正。
 */
data class WasmPluginMeta(
    val toolName: String,
    val description: String,
    val parametersJson: String,
    val entryExport: String,
) {
    companion object {
        /** 承载工具元数据的自定义段名 */
        const val SECTION_NAME = "nyral.tool"

        /** 元数据缺省 entry 字段时的入口导出名 */
        const val DEFAULT_ENTRY = "tool_call"

        /** 工具名：小写字母开头，只含小写字母数字下划线，最长 32 字符 */
        val TOOL_NAME_REGEX = Regex("^[a-z][a-z0-9_]{0,31}$")

        /** 入口导出名：字母或下划线开头，只含字母数字下划线，最长 64 字符 */
        val ENTRY_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")

        const val MAX_DESCRIPTION = 500
    }
}

/** 元数据解析结果：Ok 携带元数据；None 表示无自定义段（纯计算插件）；Fail 携带拒绝原因 */
sealed interface WasmPluginMetaResult {
    data class Ok(val meta: WasmPluginMeta) : WasmPluginMetaResult
    data object None : WasmPluginMetaResult
    data class Fail(val reason: String) : WasmPluginMetaResult
}

/**
 * 解析并校验插件工具元数据。
 *
 * 自定义段直接从二进制扫描（WASM 段结构固定，不依赖运行时内部类型）；
 * entry 指向的导出是否存在用嵌入层解析结果校验——元数据声明了不存在的入口，
 * 要么是作者笔误，要么是故意让模型调一个跑不起来的东西，两种都不该装。
 */
fun parseWasmPluginMeta(wasmBytes: ByteArray): WasmPluginMetaResult {
    val data = readCustomSection(wasmBytes, WasmPluginMeta.SECTION_NAME)
        ?: return WasmPluginMetaResult.None

    val raw = data.toString(Charsets.UTF_8)
    // org.json 对无引号字符串、单引号等过于宽容（真机与 JVM 实现都一样），
    // 不可信输入先过严格 JSON 语法校验，再交给 org.json 提取字段
    if (!isStrictJson(raw)) {
        return WasmPluginMetaResult.Fail("自定义段不是合法 JSON")
    }
    val json = runCatching { JSONObject(raw) }.getOrElse {
        return WasmPluginMetaResult.Fail("自定义段不是合法 JSON")
    }

    val toolName = json.optString("name")
    if (!WasmPluginMeta.TOOL_NAME_REGEX.matches(toolName)) {
        return WasmPluginMetaResult.Fail("工具名不合法：$toolName")
    }

    val description = json.optString("description")
    if (description.isBlank() || description.length > WasmPluginMeta.MAX_DESCRIPTION) {
        return WasmPluginMetaResult.Fail("工具描述缺失或超过 ${WasmPluginMeta.MAX_DESCRIPTION} 字符")
    }

    // 参数 schema 必须是 JSON 对象（空对象表示无参数）；重新序列化保证存储口径一致
    val parametersJson = if (json.has("parameters")) {
        val params = json.opt("parameters")
        if (params !is JSONObject) {
            return WasmPluginMetaResult.Fail("parameters 必须是 JSON 对象")
        }
        params.toString()
    } else {
        "{}"
    }

    val entry = if (json.has("entry")) {
        val e = json.optString("entry")
        if (!WasmPluginMeta.ENTRY_REGEX.matches(e)) {
            return WasmPluginMetaResult.Fail("入口导出名不合法：$e")
        }
        e
    } else {
        WasmPluginMeta.DEFAULT_ENTRY
    }

    val exports = when (val r = module(wasmBytes)) {
        is ChasmResult.Success -> r.result.exports.map { it.name }
        is ChasmResult.Error -> return WasmPluginMetaResult.Fail("模块解析失败: ${r.error}")
    }
    if (entry !in exports) {
        return WasmPluginMetaResult.Fail("入口导出 $entry 不存在于模块")
    }

    return WasmPluginMetaResult.Ok(WasmPluginMeta(toolName, description, parametersJson, entry))
}

/**
 * 从 WASM 二进制扫描自定义段：跳过魔数与版本号，逐段读段 id 与长度（LEB128），
 * 段 id 0 为自定义段，段名后即段内容。找不到返回 null。
 */
private fun readCustomSection(wasmBytes: ByteArray, sectionName: String): ByteArray? {
    if (wasmBytes.size < 8) return null
    var pos = 8

    fun readLeb128(): Int {
        var result = 0
        var shift = 0
        while (pos < wasmBytes.size) {
            val byte = wasmBytes[pos++].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("LEB128 越过文件末尾")
    }

    return try {
        while (pos < wasmBytes.size) {
            val id = wasmBytes[pos++].toInt() and 0xFF
            val size = readLeb128()
            val contentStart = pos
            if (id == 0) {
                val nameLen = readLeb128()
                val name = String(wasmBytes, pos, nameLen, Charsets.UTF_8)
                if (name == sectionName) {
                    return wasmBytes.copyOfRange(pos + nameLen, contentStart + size)
                }
            }
            pos = contentStart + size
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * 严格 JSON 语法校验（RFC 8259）。org.json 的解析对无引号字符串值、单引号键、
 * 未转义控制字符等一律放行，而自定义段是不可信输入——宽松语法会让畸形元数据
 * 混过安装期校验。这里只做语法判定，字段提取仍交给 org.json。
 */
internal fun isStrictJson(text: String): Boolean = try {
    StrictJsonValidator(text).validate()
    true
} catch (e: StrictJsonValidator.Invalid) {
    false
}

internal class StrictJsonValidator(private val text: String) {
    class Invalid : Exception()

    private var i = 0

    fun validate() {
        skipWs()
        parseValue()
        skipWs()
        if (i != text.length) throw Invalid()
    }

    private fun fail(): Nothing = throw Invalid()

    private fun skipWs() {
        while (i < text.length && text[i] in " \t\n\r") i++
    }

    private fun parseValue() {
        skipWs()
        if (i >= text.length) fail()
        when (text[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true")
            'f' -> parseLiteral("false")
            'n' -> parseLiteral("null")
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber()
            else -> fail()
        }
    }

    private fun parseLiteral(word: String) {
        if (!text.startsWith(word, i)) fail()
        i += word.length
    }

    private fun parseString() {
        i++ // 开引号
        while (true) {
            if (i >= text.length) fail()
            when (val c = text[i]) {
                '"' -> { i++; return }
                '\\' -> {
                    i++
                    if (i >= text.length) fail()
                    when (text[i]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> i++
                        'u' -> {
                            i++
                            repeat(4) {
                                if (i >= text.length || text[i] !in "0123456789abcdefABCDEF") fail()
                                i++
                            }
                        }
                        else -> fail()
                    }
                }
                else -> {
                    if (c < ' ') fail() // 未转义控制字符
                    i++
                }
            }
        }
    }

    private fun parseNumber() {
        if (text[i] == '-') i++
        if (i >= text.length) fail()
        when (text[i]) {
            '0' -> i++
            in '1'..'9' -> while (i < text.length && text[i] in '0'..'9') i++
            else -> fail()
        }
        if (i < text.length && text[i] == '.') {
            i++
            if (i >= text.length || text[i] !in '0'..'9') fail()
            while (i < text.length && text[i] in '0'..'9') i++
        }
        if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
            i++
            if (i < text.length && (text[i] == '+' || text[i] == '-')) i++
            if (i >= text.length || text[i] !in '0'..'9') fail()
            while (i < text.length && text[i] in '0'..'9') i++
        }
    }

    private fun parseObject() {
        i++ // {
        skipWs()
        if (i < text.length && text[i] == '}') { i++; return }
        while (true) {
            skipWs()
            if (i >= text.length || text[i] != '"') fail() // 键必须是带引号字符串
            parseString()
            skipWs()
            if (i >= text.length || text[i] != ':') fail()
            i++
            parseValue()
            skipWs()
            if (i >= text.length) fail()
            when (text[i]) {
                ',' -> i++
                '}' -> { i++; return }
                else -> fail()
            }
        }
    }

    private fun parseArray() {
        i++ // [
        skipWs()
        if (i < text.length && text[i] == ']') { i++; return }
        while (true) {
            parseValue()
            skipWs()
            if (i >= text.length) fail()
            when (text[i]) {
                ',' -> i++
                ']' -> { i++; return }
                else -> fail()
            }
        }
    }
}
