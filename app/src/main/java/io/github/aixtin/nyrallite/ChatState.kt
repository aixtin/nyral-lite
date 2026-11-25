package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.ChatTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** 聊天运行时状态：只放数据与共享引用，具体动作由 [RoundFlow] 与 ChatRound 完成。 */
internal class ChatState(
    val activity: AppCompatActivity,
    val rv: RecyclerView,
    val adapter: ChatAdapter,
    val messages: MutableList<Message>,
    val input: EditText,
    val btnSend: Button,
    val history: MutableList<ChatTurn>,
    val cards: ToolCards,
    val onChanged: () -> Unit,
    private val contextBudget: () -> Int,
) {
    val handler = Handler(Looper.getMainLooper())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var job: Job? = null

    /** 当前流式消息在 messages 里的下标，-1 表示没有在流式。 */
    var streamingPos = -1

    /** 是否有一轮请求（含工具执行）在跑：决定按钮是"发送"还是"停止"。 */
    var busy = false

    /** 每次发送最多提示一次上下文精简，避免工具轮重复 Toast。 */
    var contextNoticeShown = false

    /** 本轮是否真的裁剪过：决定收尾后是否强制更新摘要。 */
    var trimmedThisRound = false

    /** 读取配置的预算；异常或过小都回落到默认值，避免配置损坏导致发不出消息。 */
    fun budget(): Int = runCatching { contextBudget() }
        .getOrDefault(ContextWindow.DEFAULT_MAX_TOKENS)
        .coerceAtLeast(MIN_CONTEXT_TOKENS)

    private companion object {
        /** 配置损坏时也不至于小到每轮都裁剪。 */
        const val MIN_CONTEXT_TOKENS = 4_000
    }
}
