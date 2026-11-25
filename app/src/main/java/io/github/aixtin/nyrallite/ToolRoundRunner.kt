package io.github.aixtin.nyrallite

import android.os.SystemClock
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.ToolCall
import io.github.aixtin.nyrallite.contract.ToolRegistry
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.contract.ToolSourceHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工具轮执行器：取工具清单、逐个执行、把过程写进会话历史。
 * 只负责"工具 ↔ 历史轮次"的翻译，不碰界面与传输。
 */
class ToolRoundRunner(
    private val registry: ToolRegistry?,
    private val history: MutableList<ChatTurn>,
) {

    /** 工具清单加载结果：未配置服务器不算失败；连不上时 [error] 带原因。 */
    data class ToolLoad(
        val specs: List<ToolSpec>,
        val error: String? = null,
    )

    /** 本轮可声明的工具；未配置服务器时为空且不算错误。 */
    suspend fun loadSpecs(): ToolLoad {
        val current = registry ?: return ToolLoad(emptyList())
        return try {
            // 聚合注册表会隔离单个来源的失败，原因从健康接口补读，避免静默降级
            val specs = current.list()
            val warning = (current as? ToolSourceHealth)?.lastErrors()?.firstOrNull()
            ToolLoad(specs, warning)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ToolRoundRunner", "取工具清单失败: ${e.message}", e)
            ToolLoad(emptyList(), e.message ?: e.toString())
        }
    }

    /**
     * 执行一批调用：先记 assistant(tool_calls)，再逐个执行并追加 tool 结果。
     * 返回 false 表示没有可用的工具注册表（调用方直接收尾）。
     * [onResult] 在 IO 线程回调每次执行的结果与耗时，供界面更新卡片。
     */
    suspend fun execute(
        calls: List<ToolCall>,
        assistantContent: String,
        onStatus: (String) -> Unit,
        onResult: (ToolCall, ToolResult, Long) -> Unit = { _, _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        val current = registry ?: return@withContext false
        history.add(ChatTurn("assistant", assistantContent, toolCalls = calls))
        var index = 0
        try {
            while (index < calls.size) {
                val call = calls[index]
                onStatus(call.name)
                val startedAt = SystemClock.elapsedRealtime()
                val result = try {
                    current.call(call.name, call.arguments)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolResult("工具调用失败：${e.message}", true)
                }
                onResult(call, result, SystemClock.elapsedRealtime() - startedAt)
                history.add(ChatTurn("tool", result.content, toolCallId = call.id))
                index++
            }
        } catch (e: CancellationException) {
            // 取消也要补齐剩余 tool 结果，否则历史里的 tool_calls 不完整，后续请求会被拒
            while (index < calls.size) {
                history.add(ChatTurn("tool", "已取消", toolCallId = calls[index].id))
                onResult(calls[index], ToolResult("已取消", true), 0L)
                index++
            }
            throw e
        }
        true
    }
}
