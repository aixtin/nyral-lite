package io.github.aixtin.nyrallite.plugins.js

import android.content.Context
import java.io.File

/**
 * JS 工具文件仓库：filesDir/js_tools/<id>.js 的列表、安装与卸载。
 *
 * 与 WasmPluginRepo 同构：单文件即工具（脚本头部内嵌 nyral.tool 元数据），
 * 卸载即删文件；重装同一文件会因哈希对不上重新弹窗确认。
 */
object JsToolRepo {

    private fun dir(context: Context): File = File(context.filesDir, "js_tools")

    /** 已安装工具 id 列表（文件名去扩展名），按字母序 */
    fun list(context: Context): List<String> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.extension == "js" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun scriptFile(context: Context, toolId: String): File = File(dir(context), "$toolId.js")

    fun load(context: Context, toolId: String): String? = runCatching {
        scriptFile(context, toolId).readText()
    }.getOrNull()

    /** 安装：保存脚本文件；失败返回 false（调用方提示用户） */
    fun install(context: Context, toolId: String, script: String): Boolean = runCatching {
        dir(context).mkdirs()
        scriptFile(context, toolId).writeText(script)
    }.isSuccess

    /** 卸载：删脚本文件；文件本就不存在仍视为成功 */
    fun uninstall(context: Context, toolId: String): Boolean {
        val f = scriptFile(context, toolId)
        return !f.exists() || f.delete()
    }
}
