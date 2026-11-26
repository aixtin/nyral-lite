package io.github.aixtin.nyrallite

import android.content.Context
import android.view.inputmethod.InputMethodManager
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatTurn
import kotlinx.coroutines.launch

/** 发送与重试入口：整理用户消息、切换按钮状态、启动一轮请求。 */
internal class ChatSender(
    private val state: ChatState,
    private val flow: RoundFlow,
    private val round: ChatRound,
    private val takeAttachments: () -> List<AttachmentRef>,
) {

    fun send() {
        if (state.busy) return
        val text = state.input.text.toString().trim()
        val attachments = takeAttachments()
        if (text.isEmpty() && attachments.isEmpty()) return
        // 用户不点重试、改为重新发送时，移除上一条临时错误卡片
        if (state.messages.lastOrNull()?.role == Message.ROLE_ERROR) {
            state.messages.removeAt(state.messages.lastIndex)
            state.adapter.notifyItemRemoved(state.messages.size)
        }
        state.input.setText("")

        state.messages.add(Message(Message.ROLE_USER, text, attachments = attachments))
        state.history.add(ChatTurn(ChatTurn.ROLE_USER, text, attachments = attachments))
        state.adapter.notifyItemInserted(state.messages.lastIndex)

        start()
        // 发送后收起键盘, 避免挡住正在生成的回复
        val imm = state.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(state.input.windowToken, 0)

        // 用户消息先落盘，即使这一轮失败/被杀也能在历史里找回
        state.onChanged()
        state.job = state.scope.launch { round.run() }
    }

    /** 重试当前失败请求：复用已有历史，不重复追加用户消息。 */
    fun retry(position: Int) {
        if (state.busy || position != state.messages.lastIndex ||
            state.messages.getOrNull(position)?.role != Message.ROLE_ERROR
        ) {
            return
        }
        state.messages.removeAt(position)
        state.adapter.notifyItemRemoved(position)
        start()
        state.job = state.scope.launch { round.run() }
    }

    /** 进入"本轮进行中"状态：新建流式消息，按钮切成"停止"。 */
    private fun start() {
        flow.startRound()
        state.busy = true
        state.contextNoticeShown = false
        state.btnSend.setText(R.string.stop)
    }
}
