package io.github.aixtin.nyrallite.plugins.wasm

import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.embedding.validate
import java.security.MessageDigest

/**
 * WASM 插件运行时：哈希校验 → 解码 → 校验 → 实例化 → 调用。
 *
 * 信任模型：加载前必须校验文件哈希与 WasmPluginStore 登记一致，
 * 哈希对不上视为未信任，拒绝加载（更新即再弹窗的执行点在安装流程）。
 */
object WasmPluginRuntime {

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 加载结果：成功携带 store 与 instance，失败携带原因（用于弹窗与审计） */
    sealed interface LoadResult {
        data class Ok(val store: Store, val instance: Instance) : LoadResult
        data class Fail(val reason: String) : LoadResult
    }

    fun loadTrusted(
        pluginId: String,
        wasmBytes: ByteArray,
        trustedSha256: String?,
        gateway: WasmPluginGateway,
    ): LoadResult {
        val actual = sha256(wasmBytes)
        if (trustedSha256 == null) {
            return LoadResult.Fail("插件 $pluginId 未登记信任，拒绝加载")
        }
        if (!actual.equals(trustedSha256, ignoreCase = true)) {
            return LoadResult.Fail("插件 $pluginId 文件哈希与登记不符（可能已更新未确认），拒绝加载")
        }
        return load(wasmBytes, gateway)
    }

    /** 不做信任检查的加载，仅供安装流程预览能力声明时使用 */
    fun load(wasmBytes: ByteArray, gateway: WasmPluginGateway): LoadResult {
        val module = when (val r = module(wasmBytes)) {
            is ChasmResult.Success -> r.result
            is ChasmResult.Error -> return LoadResult.Fail("解码失败: ${r.error}")
        }
        when (val v = validate(module)) {
            is ChasmResult.Error -> return LoadResult.Fail("校验失败: ${v.error}")
            else -> {}
        }
        // 导入与实例必须绑定同一个 Store，否则报 Importable belongs to a different Store
        val store = store()
        return when (val r = instance(store, module, gateway.imports(store))) {
            is ChasmResult.Success -> LoadResult.Ok(store, r.result)
            is ChasmResult.Error -> LoadResult.Fail("实例化失败: ${r.error}")
        }
    }

    /** 调用导出函数，返回结果描述或错误原因 */
    fun call(instance: Instance, store: Store, export: String): String =
        when (val r = invoke(store, instance, export)) {
            is ChasmResult.Success -> r.result.toString()
            is ChasmResult.Error -> "调用失败: ${r.error}"
        }
}
