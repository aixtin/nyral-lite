package io.github.aixtin.nyrallite.contract

/** 一次流式回复中，传输插件向主体上报的事件。 */
sealed interface ChatEvent {
    /** 一帧增量：正文 + 思考（可能分别为空）。 */
    data class Delta(val content: String, val reasoning: String) : ChatEvent

    /**
     * 本轮结束前汇总出的工具调用请求（一个流里可累计多个）。
     * 传输插件在 [Done] 之前发出；主体执行完后把结果作为历史轮次续跑。
     */
    data class ToolCalls(val calls: List<ToolCall>) : ChatEvent

    /** 网络流正常结束。 */
    data object Done : ChatEvent

    /** 请求失败（已转成人话）。 */
    data class Error(val message: String) : ChatEvent
}
