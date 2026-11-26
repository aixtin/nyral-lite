package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.ChatEvent
import io.github.aixtin.nyrallite.contract.ChatRequest
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.contract.ChatTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 会话滚动摘要：每积累若干条新消息，后台把「旧摘要 + 新增对话」压成要点。
 *
 * 故意不动原文，只让发送窗口有损：原文仍在会话文件里，模型需要细节时
 * 用 history_search 取。失败静默，下一轮攒够条数会自然重试。
 */
class SessionSummarizer(
    private val transport: ChatTransport,
    private val onSummary: (sessionId: String, summary: String, upTo: Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun maybeSummarize(
        sessionId: String?,
        previous: String,
        upTo: Int,
        messages: List<Message>,
        force: Boolean = false,
    ) {
        val id = sessionId ?: return
        // 正常按条数触发；本轮发生过裁剪则强制追平，避免丢掉没进摘要的内容
        if (messages.size - upTo < if (force) 1 else MIN_NEW_MESSAGES) return
        if (!running.compareAndSet(false, true)) return
        val snapshot = messages.toList()
        val target = snapshot.size
        scope.launch {
            try {
                val text = SummaryText.normalize(summarize(previous, snapshot.subList(upTo, target)))
                if (text.isNotEmpty()) onSummary(id, text, target)
            } catch (_: Exception) {
                // 失败静默：不写水位，下一轮会再试
            } finally {
                running.set(false)
            }
        }
    }

    fun dispose() = scope.cancel()

    private suspend fun summarize(previous: String, fresh: List<Message>): String {
        val transcript = fresh
            .filter { (it.role == Message.ROLE_USER || it.role == Message.ROLE_AI) && it.content.isNotBlank() }
            .takeLast(MAX_INPUT_MESSAGES)
            .joinToString("\n") { msg ->
                val who = if (msg.role == Message.ROLE_USER) "用户" else "助手"
                "$who: ${msg.content.take(MAX_CHARS_PER_MESSAGE)}"
            }
        if (transcript.isBlank()) return ""
        val system = buildString {
            append(PROMPT)
            if (previous.isNotBlank()) {
                append("\n\n已有摘要（在此基础上合并更新，仍有效的要点不要丢）：\n")
                append(previous)
            }
        }
        val raw = StringBuilder()
        suspendCancellableCoroutine { cont ->
            val job = transport.stream(
                ChatRequest(
                    systemPrompt = system,
                    turns = listOf(ChatTurn(ChatTurn.ROLE_USER, "新增对话：\n$transcript")),
                ),
            ) { event ->
                when (event) {
                    is ChatEvent.Delta -> raw.append(event.content)
                    is ChatEvent.ToolCalls -> Unit
                    ChatEvent.Done, is ChatEvent.Error -> if (cont.isActive) cont.resume(Unit)
                }
            }
            cont.invokeOnCancellation { job.cancel() }
        }
        return raw.toString()
    }

    private companion object {
        const val MIN_NEW_MESSAGES = 6
        const val MAX_INPUT_MESSAGES = 30
        const val MAX_CHARS_PER_MESSAGE = 300

        const val PROMPT = "你在为一段长期对话维护滚动摘要。把【新增对话】合并进已有摘要，输出更新后的完整摘要。" +
            "要求：最多 6 行，每行以「- 」开头，总字数不超过 300 字；只保留用户的目标与设定、已确认的决定、" +
            "未完成事项、关键专有名词与数字；不写寒暄、语气点评和一次性细节；不要编造对话里没有的内容。" +
            "直接输出要点，不要标题、序号说明或代码块。"
    }
}
