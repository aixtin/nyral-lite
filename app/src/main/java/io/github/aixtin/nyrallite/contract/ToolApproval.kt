package io.github.aixtin.nyrallite.contract

/**
 * 工具执行前的人机确认入口：由主体实现（系统对话框），安全插件在门禁里调用。
 * 插件不能直接弹窗，只能通过这个契约请求主体。
 */
interface ToolApproval {
    /** 挂起直到用户做出选择；没有可弹窗界面时返回 [ApprovalDecision.UNAVAILABLE]。 */
    suspend fun ask(pluginId: String, tool: String, arguments: String): ApprovalDecision
}

/** 用户对一次工具调用的决定。 */
enum class ApprovalDecision {
    /** 只允许这一次。 */
    ALLOW_ONCE,

    /** 本进程会话内该工具不再询问。 */
    ALLOW_SESSION,

    /** 拒绝执行。 */
    DENY,

    /** 确认通道不可用（无前台界面等）。 */
    UNAVAILABLE,
}
