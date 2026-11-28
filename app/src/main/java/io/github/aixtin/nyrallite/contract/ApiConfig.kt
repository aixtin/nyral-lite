package io.github.aixtin.nyrallite.contract

/** API 配置模型：字段就是界面上那几项，不掺厂商私有项。 */
data class ApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** 发送预算（tokens），0 表示用默认值；只影响发送窗口，不删本地原文。 */
    val contextTokens: Int = 0,
    /** 单条消息内工具轮上限，0 表示用默认值。 */
    val toolRounds: Int = 0,
)
