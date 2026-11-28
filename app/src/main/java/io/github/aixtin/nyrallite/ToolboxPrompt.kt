package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.plugins.js.JsToolMetaResult
import io.github.aixtin.nyrallite.plugins.js.JsToolRepo
import io.github.aixtin.nyrallite.plugins.js.JsToolSource
import io.github.aixtin.nyrallite.plugins.js.JsToolStore
import io.github.aixtin.nyrallite.plugins.js.parseJsToolMeta

/**
 * 自造工具箱提示块：告诉模型它能给自己写工具，以及铸造流程与防骚扰约定。
 * 已装 JS 工具只列名字、能力与草稿态，正文靠工具描述与试跑结果按需取。
 * 任一环节失败返回空串，不影响主对话。
 */
internal object ToolboxPrompt {

    fun blockFor(app: App): String {
        val lines = ArrayList<String>()
        val store = JsToolStore(app)
        for (toolId in JsToolRepo.list(app)) {
            val script = JsToolRepo.load(app, toolId) ?: continue
            val meta = when (val r = parseJsToolMeta(script)) {
                is JsToolMetaResult.Ok -> r.meta
                is JsToolMetaResult.Fail -> continue
            }
            val caps = if (meta.capabilities.isEmpty()) "纯计算" else meta.capabilities.joinToString("/")
            val draft = if (store.get(toolId) == null) "，草稿" else ""
            lines.add("- ${JsToolSource.toolName(toolId)}：${meta.name}（$caps$draft）")
        }
        val toolbox = if (lines.isEmpty()) "（暂无）" else lines.joinToString("\n")
        return "\n\n[自造工具]\n" +
            "你可以用 write_js_tool 给自己写 JS 工具。脚本第一行必须是：" +
            "/* nyral.tool {\"name\":\"展示名\",\"description\":\"一句话\",\"contractVersion\":\"1.0\",\"capabilities\":[\"http_fetch\"]} */" +
            "（JSON 紧跟 nyral.tool，不要写成 Javadoc 键值行）。\n" +
            "脚本在 QuickJS 全局作用域运行：宿主入口 nyral.invoke(\"http_fetch\", JSON.stringify({url:\"...\", offset:0})) " +
            "返回响应正文文本或 null；HTML 页面自动提取正文（剥掉标签/脚本/导航），非 HTML 原样返回；" +
            "offset 可选，跳过响应体前 offset 字节再读取，用于超大内容的分段抓取；" +
            "顶层不能用 return，脚本最后一个表达式的值就是工具结果。\n" +
            "流程：write_js_tool 落草稿，直接调用 js_<toolId> 试跑（草稿不弹窗）；" +
            "确认可用后调用 finalize_js_tool 定稿（默认弹窗确认一次，门禁放行档自动登记）；" +
            "定稿后脚本文件被改动会被拒绝运行，需重新定稿。\n" +
            "用户拒绝过的工具，本次会话内不要再提交同一工具。\n" +
            "当前已装 JS 工具：\n$toolbox"
    }
}
