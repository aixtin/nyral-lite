package io.github.aixtin.nyrallite

import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.ToolRegistry
import kotlinx.coroutines.cancel

/**
 * 会话编排：发送、流式请求、工具轮、历史维护、按钮状态。
 *
 * 分工：
 *  - 运行状态集中在 [ChatState]；
 *  - 一轮的请求与工具循环交给 [ChatRound]；
 *  - 轮次开始与收尾交给 [RoundFlow]，发送入口交给 [ChatSender]；
 *  - 文本逐步上屏与贴底交给 [Typewriter]，工具卡片交给 [ToolCards]；
 *  - 布局与滚动按钮交互留在 MainActivity。
 *
 * 工具轮是"流式 → 执行工具 → 再流式"的循环，最多 [ChatRound] 里的轮次上限；
 * 每一轮都有独立的 AI 消息，工具卡片插在两条 AI 消息之间，保持真实发生顺序。
 * 会话历史单独维护（而不是从消息列表反推），因为工具轮需要 assistant(tool_calls)
 * 与 tool 结果这类不显示在界面上的轮次。
 */
class ChatController(
    private val activity: AppCompatActivity,
    private val rv: RecyclerView,
    private val adapter: ChatAdapter,
    private val messages: MutableList<Message>,
    private val input: EditText,
    private val btnSend: Button,
    private val transport: ChatTransport,
    toolRegistry: ToolRegistry?,
    /** 每轮重新读取当前会话绑定，切换人设不会修改任何历史消息。 */
    private val currentPersonaId: () -> String? = { null },
    /** 当前会话的前情摘要块；空串表示还没有摘要。 */
    private val sessionSummary: () -> String = { "" },
    /** 本轮发送预算；配置页留空时回落到默认值。 */
    private val contextBudget: () -> Int = { ContextWindow.DEFAULT_MAX_TOKENS },
    /** 一轮完整结束（非取消）后通知外部，用于后台更新摘要；force 表示本轮发生过裁剪。 */
    private val onRoundEnd: (force: Boolean) -> Unit = {},
    /** 取出并清空待发送附件；只有文本和附件都为空时才不发。 */
    private val takePendingAttachments: () -> List<AttachmentRef> = { emptyList() },
    /** 消息变化时通知外部落盘（防抖由调用方实现）。 */
    private val onChanged: () -> Unit = {},
) {
    private val history = mutableListOf<ChatTurn>()
    private val cards = ToolCards(messages, adapter, rv, onChanged)
    private val state = ChatState(
        activity, rv, adapter, messages, input, btnSend, history, cards, onChanged, contextBudget,
    )
    private val runner = ToolRoundRunner(toolRegistry, history)
    private val typewriter: Typewriter = Typewriter(rv, adapter, messages) { flow.onDrained() }
    private val flow: RoundFlow = RoundFlow(state, typewriter, onRoundEnd)
    private val round = ChatRound(
        state, typewriter, flow, runner, transport, currentPersonaId, sessionSummary,
    ) { message -> onToolsWarning?.invoke(message) }
    private val sender = ChatSender(state, flow, round, takePendingAttachments)

    /** 工具来源告警（多半是 MCP 连不上）：null 表示已恢复，由界面注入。 */
    var onToolsWarning: ((String?) -> Unit)? = null

    /** 用户手指/惯性滚动进行中：暂停流式渲染。 */
    /** 本次滚动是否由用户拖动引起；只有这种停稳才重新判定跟随。 */
    private var sawDrag = false

    /** 列表滚动状态：滚动中暂停流式渲染；用户拖动停稳后再按是否在底部决定跟随。 */
    var listScrollState: Int = RecyclerView.SCROLL_STATE_IDLE
        set(value) {
            field = value
            typewriter.paused = value != RecyclerView.SCROLL_STATE_IDLE
            when (value) {
                RecyclerView.SCROLL_STATE_DRAGGING -> sawDrag = true
                RecyclerView.SCROLL_STATE_IDLE -> if (sawDrag) {
                    // 只有用户主动拖过才重新判定，避免布局/键盘引起的空 IDLE 误关跟随
                    sawDrag = false
                    typewriter.followAnswer = ScrollPinner.nearBottom(rv, toleranceDp = 48)
                    android.util.Log.d("TW", "idle-after-drag follow=${typewriter.followAnswer}")
                }
            }
        }

    fun onSendButtonClick() {
        if (state.busy) {
            state.job?.cancel()
        } else {
            sender.send()
        }
    }

    fun pinToBottom() {
        // 手动回到底部：恢复跟随，后续新内容继续贴底
        typewriter.followAnswer = true
        ScrollPinner.pin(rv)
    }

    /**
     * 切换/恢复会话：清空运行时状态，用给定消息重建界面与请求历史。
     * 工具轮细节不落盘，恢复后每轮 AI 正文按一条 assistant 历史对待。
     */
    fun replaceSession(loaded: List<Message>) {
        state.job?.cancel()
        typewriter.stop()
        state.history.clear()
        state.messages.clear()
        state.trimmedThisRound = false
        state.messages.addAll(loaded)
        state.history.addAll(HistoryRebuilder.rebuild(loaded))
        state.streamingPos = -1
        state.cards.reset()
        state.busy = false
        state.btnSend.setText(R.string.send)
        state.adapter.notifyDataSetChanged()
        if (state.messages.isNotEmpty()) state.rv.post { ScrollPinner.pinStable(state.rv) }
    }

    /** 人设开场白：只在空会话里插入，作为第一条 AI 消息；有内容时不动。 */
    fun insertGreeting(text: String) {
        if (text.isBlank() || state.messages.isNotEmpty()) return
        state.messages.add(Message("ai", text))
        state.history.add(ChatTurn("assistant", text))
        state.adapter.notifyItemInserted(0)
        state.rv.post { ScrollPinner.pinStable(state.rv) }
        onChanged()
    }

    /** 重试当前失败请求：复用已有历史，不重复追加用户消息。 */
    fun retry(position: Int) = sender.retry(position)

    fun onDestroy() {
        typewriter.stop()
        state.job?.cancel()
        state.scope.cancel()
    }
}
