package io.github.aixtin.nyrallite.plugins.system

import io.github.aixtin.nyrallite.contract.ClipboardContract
import io.github.aixtin.nyrallite.contract.LocationContract
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import org.json.JSONObject

/**
 * 设备能力工具来源：定位 + 剪贴板。
 *
 * 这两个能力是容器型应用（WebView/云端容器）拿不到的——
 * 定位依赖系统 LocationManager，剪贴板受 Android 10+ 前台限制，
 * 只有原生宿主进程能直接调用。
 */
class DeviceToolSource(
    private val location: LocationContract,
    private val clipboard: ClipboardContract,
) : ToolSource {

    override val sourceId: String = "builtin:device-tools"

    override suspend fun list(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "get_location",
            description = "获取用户当前的地理位置（经纬度）。需要用户已授予定位权限；室内等 GPS 信号弱的场景会回退到网络定位或系统缓存位置，此时 accuracy 可能较大。",
            parametersJson = "{}",
        ),
        ToolSpec(
            name = "read_clipboard",
            description = "读取用户剪贴板中的文本内容。用户可能刚复制了地址、验证码、一段文字，适合用来回答「帮我看看我复制了什么」这类请求。内容超过 2000 字会被截断。",
            parametersJson = "{}",
        ),
        ToolSpec(
            name = "write_clipboard",
            description = "把一段文本写入用户剪贴板，用户随后可以在任意 App 里粘贴。适合「帮我复制这段内容」类请求。",
            parametersJson = PARAMS_WRITE_CLIPBOARD,
        ),
    )

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? =
        when (name) {
            "get_location" -> callGetLocation()
            "read_clipboard" -> callReadClipboard()
            "write_clipboard" -> callWriteClipboard(argumentsJson)
            else -> null
        }

    private suspend fun callGetLocation(): ToolResult {
        if (!location.hasPermission()) {
            return ToolResult(
                "没有定位权限。请让用户在「插件」页面授予定位权限后重试。",
                isError = true,
            )
        }
        val fix = runCatching { location.current() }.getOrNull()
            ?: location.lastKnown()
        if (fix == null) {
            return ToolResult(
                "暂时拿不到定位：设备定位服务可能已关闭，或尚未产生可用位置。",
                isError = true,
            )
        }
        return ToolResult(
            JSONObject().apply {
                put("latitude", fix.latitude)
                put("longitude", fix.longitude)
                put("accuracy_m", fix.accuracyMeters.toDouble())
                put("provider", fix.provider)
                put("timestamp", fix.timeMillis)
            }.toString(),
        )
    }

    private fun callReadClipboard(): ToolResult = runCatching {
        val text = clipboard.readText()
        if (text.isNullOrEmpty()) {
            ToolResult("剪贴板里没有文本内容。", isError = true)
        } else if (text.length > MAX_CLIPBOARD_CHARS) {
            ToolResult(text.take(MAX_CLIPBOARD_CHARS) + "\n…（已截断，原文 ${text.length} 字）")
        } else {
            ToolResult(text)
        }
    }.getOrElse { ToolResult("读取剪贴板失败: ${it.message}", isError = true) }

    private fun callWriteClipboard(json: String): ToolResult = runCatching {
        val text = runCatching { JSONObject(json).optString("text", "") }.getOrDefault("")
        if (text.isEmpty()) return ToolResult("缺少参数 text", isError = true)
        val ok = clipboard.writeText(text)
        if (ok) ToolResult("已写入剪贴板（${text.length} 字）")
        else ToolResult("写入剪贴板失败", isError = true)
    }.getOrElse { ToolResult("写入剪贴板失败: ${it.message}", isError = true) }

    private companion object {
        private const val MAX_CLIPBOARD_CHARS = 2000

        private const val PARAMS_WRITE_CLIPBOARD = """{
            "type":"object",
            "properties":{
                "text":{"type":"string","description":"要写入剪贴板的文本"}
            },
            "required":["text"]
        }"""
    }
}
