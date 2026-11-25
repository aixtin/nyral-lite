package io.github.aixtin.nyrallite

import android.widget.Toast
import io.github.aixtin.nyrallite.contract.ChatEvent
import io.github.aixtin.nyrallite.contract.ChatRequest
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.contract.ToolCall
import io.github.aixtin.nyrallite.contract.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 一轮或多轮流式：流式 → 执行工具 → 再流式，最多 [MAX_TOOL_ROUNDS] 轮。
 * 每一轮都有独立的 AI 消息，工具卡片插在两条 AI 消息之间，保持真实发生顺序。
 */
internal class ChatRound(
    private val state: ChatState,
    private val typewriter: Typewriter,
    private val flow: RoundFlow,
    private val runner: ToolRoundRunner,
    private val transport: ChatTransport,
    private val currentPersonaId: () -> String?,
    private val sessionSummary: () -> String,
    /** 工具来源告警：null 表示恢复正常，用于显示/收起标题栏提示。 */
    private val onToolsWarning: (String?) -> Unit = {},
) {

    suspend fun run() {
        try {
            val load = runner.loadSpecs()
            // 某个来源（多半是 MCP）失败时挂常驻告警条，其它工具照常可用
            onToolsWarning(load.error)
            val tools = load.specs
            var round = 0
            while (true) {
                when (val outcome = streamOnce(tools)) {
                    is RoundOutcome.Failed -> {
                        flow.showError(outcome.message)
                        return
                    }
                    is RoundOutcome.Completed -> {
                        val calls = outcome.calls
                        if (calls.isEmpty()) {
                            // Done 已声明收尾，等打字机追平后 onDrained 做最终处理
                            return
                        }
                        if (round >= MAX_TOOL_ROUNDS) {
                            typewriter.append("\n\n_（工具调用次数达到上限）_", "")
                            typewriter.finish()
                            return
                        }
                        round++
                        // 本轮 AI 已结束：先定稿，再让工具卡片跟在它后面
                        val assistantContent = flow.settleForTools()
                        state.cards.insert(calls)
                        val executed = runner.execute(
                            calls = calls,
                            assistantContent = assistantContent,
                            // 卡片自身展示运行状态，不再往思考区混入工具日志
                            onStatus = {},
                            onResult = { call, result, durationMs ->
                                state.handler.post {
                                    state.cards.applyResult(call.id, result, durationMs)
                                }
                            },
                        )
                        if (!executed) {
                            state.cards.failRunning()
                            flow.end(cancelled = false)
                            return
                        }
                        // 工具结果齐了才创建下一轮，正文和思考都排在卡片之后
                        flow.startRound()
                    }
                }
            }
        } catch (e: CancellationException) {
            state.cards.failRunning()
            flow.end(cancelled = true)
            throw e
        }
    }

    /** 发起一次流式请求，挂起直到 Done / Error / 取消。 */
    private suspend fun streamOnce(tools: List<ToolSpec>): RoundOutcome {
        // prompt 各块必须在 suspendCancellableCoroutine 外面拼好
        val finalSystemPrompt = PromptAssembler.build(
            state.activity,
            state.history,
            currentPersonaId(),
            sessionSummary,
        )
        return suspendCancellableCoroutine { cont ->
            val calls = ArrayList<ToolCall>()
            typewriter.resume()
            val fitted = ContextWindow.fit(state.history, state.budget())
            if (fitted.adjusted) state.trimmedThisRound = true
            if (fitted.adjusted && !state.contextNoticeShown) {
                Toast.makeText(
                    state.activity,
                    R.string.context_trimmed,
                    Toast.LENGTH_SHORT,
                ).show()
                state.contextNoticeShown = true
            }
            val transportJob = transport.stream(
                ChatRequest(
                    systemPrompt = finalSystemPrompt,
                    turns = fitted.turns,
                    tools = tools,
                ),
            ) { event ->
                when (event) {
                    is ChatEvent.Delta -> typewriter.append(event.content, event.reasoning)
                    is ChatEvent.ToolCalls -> {
                        calls.clear()
                        calls.addAll(event.calls)
                    }
                    ChatEvent.Done -> {
                        // 网络结束不等于显示结束: 打字机追平缓冲区后才真正收尾
                        typewriter.finish()
                        if (cont.isActive) cont.resume(RoundOutcome.Completed(calls.toList()))
                    }
                    is ChatEvent.Error -> {
                        if (cont.isActive) cont.resume(RoundOutcome.Failed(event.message))
                    }
                }
            }
            cont.invokeOnCancellation { transportJob.cancel() }
        }
    }

    private companion object {
        /** 防止模型在工具结果上无限绕圈。 */
        const val MAX_TOOL_ROUNDS = 5
    }
}
