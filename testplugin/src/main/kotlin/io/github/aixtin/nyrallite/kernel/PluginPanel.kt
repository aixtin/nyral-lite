package io.github.aixtin.nyrallite.kernel

/**
 * 可配置插件契约（声明式）。
 *
 * 插件不实现任何 UI，只返回字段描述；渲染、默认值与持久化都由宿主负责。
 * 字段值统一按字符串存取（开关存 "true"/"false"），落进该插件命名空间的 [PluginConfig]。
 */
interface ConfigurablePlugin {
    fun panel(): PanelSpec
}

/** 配置面板描述：标题 + 字段列表。 */
data class PanelSpec(
    val title: String = "",
    val fields: List<PanelField> = emptyList(),
)

/** 面板字段基类。key 在同一面板内唯一，用作配置项名。 */
sealed class PanelField {
    abstract val key: String
    abstract val label: String

    /** 字段下方的小字说明，可为空。 */
    abstract val description: String

    /** 普通文本，如 API 地址。 */
    data class Text(
        override val key: String,
        override val label: String,
        val placeholder: String = "",
        val default: String = "",
        override val description: String = "",
    ) : PanelField()

    /** 敏感文本，如 API Key；宿主默认打码显示。 */
    data class Secret(
        override val key: String,
        override val label: String,
        val placeholder: String = "",
        val default: String = "",
        override val description: String = "",
    ) : PanelField()

    /** 开关，存 "true"/"false"。 */
    data class Switch(
        override val key: String,
        override val label: String,
        val default: Boolean = false,
        override val description: String = "",
    ) : PanelField()

    /** 下拉单选，存选中项的 value。 */
    data class Select(
        override val key: String,
        override val label: String,
        val options: List<Option>,
        val default: String = "",
        override val description: String = "",
    ) : PanelField() {
        data class Option(val value: String, val label: String = value)
    }
}
