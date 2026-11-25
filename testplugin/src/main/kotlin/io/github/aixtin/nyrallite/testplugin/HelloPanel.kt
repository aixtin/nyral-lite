package io.github.aixtin.nyrallite.testplugin

import io.github.aixtin.nyrallite.kernel.PanelField
import io.github.aixtin.nyrallite.kernel.PanelSpec

/**
 * 示例配置面板：网页搜索插件常见的「地址 + Key」，再加开关与下拉。
 * 字段只描述，渲染与保存全部由宿主完成。
 */
internal fun helloPanel() = PanelSpec(
    title = "Hello 插件配置",
    fields = listOf(
        PanelField.Text(
            key = "api_base",
            label = "搜索 API 地址",
            placeholder = "https://example.com/search",
            description = "留空表示未配置，工具里读到空字符串。",
        ),
        PanelField.Secret(
            key = "api_key",
            label = "API Key",
            description = "只保存在本机配置里，面板默认打码显示。",
        ),
        PanelField.Switch(
            key = "verbose",
            label = "详细日志",
            default = false,
        ),
        PanelField.Select(
            key = "lang",
            label = "结果语言",
            options = listOf(
                PanelField.Select.Option("zh", "中文"),
                PanelField.Select.Option("en", "英文"),
            ),
            default = "zh",
        ),
    ),
)
