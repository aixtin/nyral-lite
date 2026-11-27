package io.github.aixtin.nyrallite.plugins.security

import android.os.Looper
import io.github.aixtin.nyrallite.contract.ApprovalDecision
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.ToolApproval
import io.github.aixtin.nyrallite.contract.ToolAuditEntry
import io.github.aixtin.nyrallite.contract.ToolAuditLog
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.kernel.GateResult
import io.github.aixtin.nyrallite.kernel.PluginConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具调用策略表：把配置翻译成 Allow / Block，需要人工确认时走 [ToolApproval]。
 *
 * 门禁本身是同步钩子（GateBus.check），确认需要等用户，所以在这里阻塞桥接；
 * 工具调用方运行在 IO 线程，阻塞不会卡住主线程。每次判定结果都写入 [audit]。
 */
internal class PolicyTable(
    private val config: PluginConfig,
    private val approval: ToolApproval?,
    private val audit: ToolAuditLog?,
) {

    /** 本进程内用户选择"本次会话允许"的工具，重启即失效。 */
    private val sessionAllowed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun decide(request: ToolCallRequest): GateResult {
        val rules = PolicyRules.from(config)
        val outcome = when (val verdict = rules.decide(request.tool)) {
            PolicyVerdict.Allow -> Outcome(
                GateResult.Allow,
                if (rules.mode == SecurityMode.OFF) "门禁已关闭" else "白名单放行",
            )
            is PolicyVerdict.Deny -> Outcome(GateResult.Block(verdict.reason), verdict.reason)
            PolicyVerdict.Confirm -> confirm(request)
        }
        audit?.record(
            ToolAuditEntry(
                pluginId = request.pluginId,
                tool = request.tool,
                arguments = request.arguments,
                decision = if (outcome.result is GateResult.Block) DECISION_DENY else DECISION_ALLOW,
                mode = rules.mode.raw,
                reason = outcome.reason,
            ),
        )
        return outcome.result
    }

    /** auto 档下未预授权的工具：挂起等用户选择，超时按拒绝处理。 */
    private fun confirm(request: ToolCallRequest): Outcome {
        // GateBus.check 是同步钩子，实现方必须自己找对线程：确认弹窗要靠主线程
        // Looper 才能响应用户点击，在主线程阻塞等待会直接 ANR。这里的异常会被
        // DefaultGateBus fail-closed 捕获转 Block，不会崩进程。
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "PolicyTable.confirm 被主线程调用：工具调用必须运行在 IO 线程"
        }
        val key = "${request.pluginId}/${request.tool}"
        if (key in sessionAllowed) return Outcome(GateResult.Allow, "本次会话已允许")
        val decision = runBlocking {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                approval?.ask(request.pluginId, request.tool, request.arguments)
                    ?: ApprovalDecision.UNAVAILABLE
            }
        }
        return when (decision) {
            ApprovalDecision.ALLOW_ONCE -> Outcome(GateResult.Allow, "用户允许一次")
            ApprovalDecision.ALLOW_SESSION -> {
                sessionAllowed += key
                Outcome(GateResult.Allow, "用户允许本次会话")
            }
            ApprovalDecision.DENY ->
                Outcome(GateResult.Block("用户拒绝执行工具 [${request.tool}]"), "用户拒绝")
            ApprovalDecision.UNAVAILABLE ->
                Outcome(GateResult.Block("确认通道不可用，请打开 App 后重试"), "确认通道不可用")
            null -> Outcome(GateResult.Block("等待确认超时，已拒绝工具 [${request.tool}]"), "确认超时")
        }
    }

    /** 判定结果 + 可读原因（写审计用）。 */
    private data class Outcome(val result: GateResult, val reason: String)

    private companion object {
        /** 确认最长等待时间，超时按拒绝处理。 */
        const val CONFIRM_TIMEOUT_MS = 120_000L

        const val DECISION_ALLOW = "allow"
        const val DECISION_DENY = "deny"
    }
}
