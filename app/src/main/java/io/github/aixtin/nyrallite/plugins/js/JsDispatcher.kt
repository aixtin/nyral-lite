package io.github.aixtin.nyrallite.plugins.js

/**
 * 宿主能力分发：JS 引擎回调入口，按能力名执行并返回 JSON 字符串或 null（拒绝）。
 * 每个引擎句柄绑定一个分发器（创建时注入），运行期不可更换。
 */
fun interface JsDispatcher {
    fun dispatch(cap: String, argsJson: String): String?
}
