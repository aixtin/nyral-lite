package io.github.aixtin.nyrallite

import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.ToolCall
import io.github.aixtin.nyrallite.contract.ToolResult

/** 工具卡片：插入折叠卡片、按 call.id 回填结果、取消时清理仍转圈的卡片。 */
internal class ToolCards(
    private val messages: MutableList<Message>,
    private val adapter: ChatAdapter,
    private val rv: RecyclerView,
    private val onChanged: () -> Unit,
) {
    /** call.id → 工具卡片所在消息下标（执行回调按 id 回填结果）。 */
    private var index: Map<String, Int> = emptyMap()

    /** 为一批调用插入折叠卡片。 */
    fun insert(calls: List<ToolCall>) {
        val map = HashMap<String, Int>()
        calls.forEach { call ->
            messages.add(Message("tool", "", toolCard = ToolCard(call.name, call.arguments)))
            val pos = messages.lastIndex
            if (call.id.isNotBlank()) map[call.id] = pos
            adapter.notifyItemInserted(pos)
        }
        if (calls.isNotEmpty()) rv.scrollToPosition(messages.lastIndex)
        index = map
    }

    /** 工具执行完成：回填结果、耗时与成功状态，并触发落盘。 */
    fun applyResult(callId: String, result: ToolResult, durationMs: Long) {
        // 个别模型不给 id，按"第一张仍在运行的卡片"兜底（执行是串行的）
        val pos = index[callId]
            ?: messages.indexOfFirst { it.toolCard?.running == true }.takeIf { it >= 0 }
            ?: return
        if (pos !in messages.indices) return
        val card = messages[pos].toolCard ?: return
        card.result = result.content
        card.ok = !result.isError
        card.running = false
        card.durationMs = durationMs
        adapter.notifyItemChanged(pos)
        onChanged()
    }

    /** 取消或注册表缺失：把仍显示"运行中"的卡片标成失败，避免永远转圈。 */
    fun failRunning() {
        var changed = false
        messages.forEachIndexed { pos, msg ->
            val card = msg.toolCard ?: return@forEachIndexed
            if (!card.running) return@forEachIndexed
            card.running = false
            card.ok = false
            card.result = "已取消"
            changed = true
            adapter.notifyItemChanged(pos)
        }
        if (changed) onChanged()
    }

    fun reset() {
        index = emptyMap()
    }
}
