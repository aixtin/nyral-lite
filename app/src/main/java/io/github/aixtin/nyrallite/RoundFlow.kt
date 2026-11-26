package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ChatTurn

/**
 * 一轮的开始与收尾：创建流式占位、工具轮定稿、错误卡片、结束后复位按钮。
 * 状态全部落在 [ChatState]，这里只做状态转移。
 */
internal class RoundFlow(
    private val state: ChatState,
    private val typewriter: Typewriter,
    private val onRoundEnd: (force: Boolean) -> Unit,
) {
    /** 创建一条新的流式 AI 消息；每个模型轮次对应一条，正文与思考互不串轮。 */
    fun startRound() {
        val pos = state.messages.size
        state.messages.add(Message("ai", "", streaming = true))
        state.streamingPos = pos
        typewriter.begin(pos)
        state.adapter.notifyItemInserted(pos)
        state.rv.scrollToPosition(pos)
    }

    /**
     * 工具调用出现，说明当前模型轮次已结束：把缓冲区全文写入当前 AI 消息并停止动画。
     * 返回值是交给 assistant(tool_calls) 历史的本轮正文。
     */
    fun settleForTools(): String {
        val content = typewriter.textSnapshot
        val thinking = typewriter.thinkingSnapshot
        typewriter.stop()
        if (state.streamingPos in state.messages.indices) {
            val msg = state.messages[state.streamingPos]
            msg.content = content
            msg.thinking = thinking
            msg.streaming = false
            state.adapter.updateAi(state.streamingPos, content, thinking)
        }
        state.streamingPos = -1
        // 即使工具执行时进程被杀，已完成的这一轮也能恢复
        state.onChanged()
        return content
    }

    /** 最终一轮的打字机追平缓冲区后收尾。 */
    fun onDrained() = end(cancelled = false)

    fun showError(message: String) {
        typewriter.stop()
        if (state.streamingPos in state.messages.indices) {
            val msg = state.messages[state.streamingPos]
            msg.role = "error"
            msg.content = state.activity.getString(R.string.error_with_retry, message)
            msg.thinking = ""
            msg.streaming = false
            state.adapter.notifyItemChanged(state.streamingPos)
            // 错误卡片比空占位高，布局后贴底，确保"点击重试"不被底栏遮住
            ScrollPinner.pinOnDraw(state.rv)
        }
        state.streamingPos = -1
        state.busy = false
        state.btnSend.setText(R.string.send)
        state.onChanged()
    }

    fun end(cancelled: Boolean) {
        typewriter.stop()
        if (state.streamingPos in state.messages.indices) {
            val raw = typewriter.textSnapshot
            val finalText = if (cancelled) raw + "\n\n_（已停止）_" else raw
            val msg = state.messages[state.streamingPos]
            msg.content = finalText
            msg.thinking = typewriter.thinkingSnapshot
            msg.streaming = false
            // 工具轮正文已随 assistant(tool_calls) 入历史，这里只补最终一轮
            if (msg.role == "ai" && raw.isNotBlank()) {
                state.history.add(ChatTurn("assistant", raw))
            }
            state.adapter.updateAi(state.streamingPos, finalText, msg.thinking) {
                // 取消时「（已停止）」刚追加到文末，等这段内容上屏后再贴底
                if (cancelled) ScrollPinner.pinOnDraw(state.rv)
            }
        }
        state.streamingPos = -1
        state.busy = false
        state.btnSend.setText(R.string.send)
        state.onChanged()
        if (!cancelled) {
            val force = state.trimmedThisRound
            state.trimmedThisRound = false
            runCatching { onRoundEnd(force) }
        }
    }
}
