package io.github.aixtin.nyrallite.contract

/**
 * 剪贴板能力：宿主实现，工具层消费。
 * Android 10+ 限制只有前台应用能读取剪贴板，所以调用时主应用必须在前台。
 */
interface ClipboardContract {

    /** 读取剪贴板文本；为空或不可读时返回 null。 */
    fun readText(): String?

    /** 写入剪贴板，返回是否成功。 */
    fun writeText(text: String): Boolean
}
