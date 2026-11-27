package io.github.aixtin.nyrallite.plugins.wasm

import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.HostMemory
import io.github.charlietap.chasm.host.HostModuleInstance
import io.github.charlietap.chasm.host.HostResources
import io.github.charlietap.chasm.host.HostStack
import io.github.charlietap.chasm.host.ModuleIndex
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.readUtf8String
import io.github.charlietap.chasm.host.writeI32
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType

/**
 * WASM 插件能力网关：插件永远拿不到真实能力（文件、网络、通知），
 * 只能通过这里声明的导入函数请求"效果"，网关按策略执行并审计。
 *
 * 沙箱属性：插件二进制里只有这里声明的导入，未声明的能力在模块层面不存在，
 * 网关之外无路可走。真机已验证（A14，2026-11-28）。
 *
 * 能力注入：read_file/http_fetch 的真实效果由宿主回调提供（fileProvider/httpFetcher），
 * notify 由 notifier 提供；回调缺省为 null 时对应能力一律拒绝并审计。
 * read_file 与 http_fetch 共用插件内存的约定缓冲区 RESULT_BUF，后写覆盖先写。
 */
class WasmPluginGateway(
    private val readFileAllowlist: Set<String> = emptySet(),
    private val fileProvider: (path: String) -> ByteArray? = { null },
    private val notifier: ((title: String, body: String) -> Boolean)? = null,
    private val httpFetcher: ((url: String) -> ByteArray?)? = null,
    private val onAudit: (entry: String) -> Unit = {},
) {
    val auditLog = mutableListOf<String>()
    val capturedLogs = mutableListOf<String>()

    private fun audit(entry: String) {
        auditLog += entry
        onAudit(entry)
    }

    private fun memoryAt(resources: HostResources, module: HostModuleInstance): HostMemory =
        resources.memory(module, ModuleIndex.MemoryIndex(0))

    /** log(ptr, len)：插件把消息写到自己的线性内存，网关读出来记录 */
    private val logFn = object : HostFunction {
        context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
        override fun invoke(parameters: Int, results: Int) {
            val ptr = parameters.readI32(0)
            val len = parameters.readI32(1)
            val msg = memoryAt(resources, module).readUtf8String(ptr, len)
            capturedLogs += msg
            audit("log: $msg")
        }
    }

    /**
     * read_file(path_ptr, path_len) -> i32
     * 允许：把文件内容写进插件内存的约定缓冲区，返回内容长度
     * 拒绝：审计记录后返回 -1，插件拿不到任何内容
     */
    private val readFileFn = object : HostFunction {
        context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
        override fun invoke(parameters: Int, results: Int) {
            val pathPtr = parameters.readI32(0)
            val pathLen = parameters.readI32(1)
            val mem = memoryAt(resources, module)
            val path = mem.readUtf8String(pathPtr, pathLen)
            if (path in readFileAllowlist) {
                val content = fileProvider(path)
                if (content == null) {
                    audit("read_file: $path DENIED (provider missing)")
                    results.writeI32(0, -3)
                } else if (content.size > RESULT_CAP) {
                    audit("read_file: $path DENIED (too large)")
                    results.writeI32(0, -2)
                } else {
                    mem.write(RESULT_BUF, content)
                    audit("read_file: $path ALLOWED (${content.size}B)")
                    results.writeI32(0, content.size)
                }
            } else {
                audit("read_file: $path DENIED (not in allowlist)")
                results.writeI32(0, -1)
            }
        }
    }

    /**
     * notify(title_ptr, title_len, body_ptr, body_len) -> i32
     * 效果由宿主注入的 notifier 决定；未注入或拒绝时返回 -1，插件得不到系统通知。
     */
    private val notifyFn = object : HostFunction {
        context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
        override fun invoke(parameters: Int, results: Int) {
            val titlePtr = parameters.readI32(0)
            val titleLen = parameters.readI32(1)
            val bodyPtr = parameters.readI32(2)
            val bodyLen = parameters.readI32(3)
            val mem = memoryAt(resources, module)
            val title = mem.readUtf8String(titlePtr, titleLen)
            val body = mem.readUtf8String(bodyPtr, bodyLen)
            val ok = notifier?.invoke(title, body) ?: false
            if (ok) {
                audit("notify: \"$title\" ALLOWED")
                results.writeI32(0, 0)
            } else {
                audit("notify: \"$title\" DENIED")
                results.writeI32(0, -1)
            }
        }
    }

    /**
     * http_fetch(url_ptr, url_len) -> i32
     * 真实请求由宿主注入的 httpFetcher 发起（含策略与超时），网关只搬运结果：
     * 允许：响应体写进插件内存约定缓冲区，返回长度（超上限返回 -2）
     * 拒绝/失败：返回 -1，插件拿不到任何响应
     */
    private val httpFetchFn = object : HostFunction {
        context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
        override fun invoke(parameters: Int, results: Int) {
            val urlPtr = parameters.readI32(0)
            val urlLen = parameters.readI32(1)
            val mem = memoryAt(resources, module)
            val url = mem.readUtf8String(urlPtr, urlLen)
            val resp = httpFetcher?.invoke(url)
            if (resp == null) {
                audit("http_fetch: $url DENIED (fetcher missing or rejected)")
                results.writeI32(0, -1)
            } else if (resp.size > RESULT_CAP) {
                audit("http_fetch: $url DENIED (response too large)")
                results.writeI32(0, -2)
            } else {
                mem.write(RESULT_BUF, resp)
                audit("http_fetch: $url ALLOWED (${resp.size}B)")
                results.writeI32(0, resp.size)
            }
        }
    }

    fun imports(store: Store): List<Import> {
        val logType = FunctionType(
            ResultType(listOf(ValueType.Number(NumberType.I32), ValueType.Number(NumberType.I32))),
            ResultType(emptyList()),
        )
        val readType = FunctionType(
            ResultType(listOf(ValueType.Number(NumberType.I32), ValueType.Number(NumberType.I32))),
            ResultType(listOf(ValueType.Number(NumberType.I32))),
        )
        val notifyType = FunctionType(
            ResultType(
                listOf(
                    ValueType.Number(NumberType.I32),
                    ValueType.Number(NumberType.I32),
                    ValueType.Number(NumberType.I32),
                    ValueType.Number(NumberType.I32),
                ),
            ),
            ResultType(listOf(ValueType.Number(NumberType.I32))),
        )
        return listOf(
            Import(MODULE_NAME, "log", function(store, logType, logFn)),
            Import(MODULE_NAME, "read_file", function(store, readType, readFileFn)),
            Import(MODULE_NAME, "notify", function(store, notifyType, notifyFn)),
            Import(MODULE_NAME, "http_fetch", function(store, readType, httpFetchFn)),
        )
    }

    companion object {
        const val MODULE_NAME = "nyral"
        const val RESULT_BUF = 1024
        const val RESULT_CAP = 256
    }
}
