package io.github.aixtin.nyrallite.plugins.wasm

import android.content.Context
import java.io.File

/**
 * WASM 插件文件仓库：filesDir/wasm_plugins/<id>.wasm 的列表与卸载。
 *
 * 卸载 = 删文件 + 吊销信任登记（WasmPluginStore.remove），
 * 与 TrustedApkStore「卸载即 revoke」同构；重装同一文件会因哈希对不上重新弹窗。
 */
object WasmPluginRepo {

    private fun dir(context: Context): File = File(context.filesDir, "wasm_plugins")

    /** 已安装插件 id 列表（文件名去扩展名），按字母序 */
    fun list(context: Context): List<String> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.extension == "wasm" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun file(context: Context, pluginId: String): File = File(dir(context), "$pluginId.wasm")

    /** 卸载：删插件文件并吊销信任；文件本就不存在时只清登记，仍视为成功 */
    fun uninstall(context: Context, pluginId: String): Boolean {
        val f = file(context, pluginId)
        val deleted = !f.exists() || f.delete()
        WasmPluginStore(context).remove(pluginId)
        return deleted
    }
}
