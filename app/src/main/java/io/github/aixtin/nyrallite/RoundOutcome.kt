package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ToolCall

/** 一次流式请求的结局：正常结束（可能带工具调用）或失败。 */
internal sealed interface RoundOutcome {
    data class Completed(val calls: List<ToolCall>) : RoundOutcome
    data class Failed(val message: String) : RoundOutcome
}
