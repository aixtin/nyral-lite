package io.github.aixtin.nyrallite

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * 工具调用卡片：折叠时只显示一行「工具 · 名称 / 状态 · 耗时」，
 * 点击展开参数与结果（超长截断，避免长结果把列表撑爆）。
 */
class ToolCallHolder(
    itemView: View,
    onToggle: (ToolCallHolder) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val title = itemView.findViewById<TextView>(R.id.tool_name)
    private val status = itemView.findViewById<TextView>(R.id.tool_status)
    private val detail = itemView.findViewById<TextView>(R.id.tool_detail)

    init {
        // 点击落在内层卡片上，根布局只负责外边距
        itemView.findViewById<View>(R.id.tool_card).setOnClickListener { onToggle(this) }
    }

    fun bind(card: ToolCard?) {
        if (card == null) return
        val context = itemView.context
        title.text = context.getString(R.string.tool_card_title, card.name)
        when {
            card.running -> {
                status.text = context.getString(R.string.tool_card_running)
                status.setTextColor(ContextCompat.getColor(context, R.color.bar_action_text_muted))
            }
            card.ok -> {
                status.text = context.getString(R.string.tool_card_ok, durationText(card.durationMs))
                status.setTextColor(ContextCompat.getColor(context, R.color.tool_ok))
            }
            else -> {
                status.text = context.getString(R.string.tool_card_failed, durationText(card.durationMs))
                status.setTextColor(ContextCompat.getColor(context, R.color.tool_fail))
            }
        }
        detail.visibility = if (card.expanded) View.VISIBLE else View.GONE
        if (card.expanded) detail.text = detailText(card)
    }

    private fun detailText(card: ToolCard): String = buildString {
        val context = itemView.context
        append(context.getString(R.string.tool_card_args, clipped(card.arguments.ifBlank { "—" })))
        if (card.result.isNotBlank()) {
            append("\n\n")
            append(context.getString(R.string.tool_card_result, clipped(card.result)))
        }
    }

    /** 展开即显示全文，只对极端超长输出做兜底截断，避免单条撑爆布局。 */
    private fun clipped(text: String): String =
        if (text.length <= MAX_CHARS) text
        else text.take(MAX_CHARS) + itemView.context.getString(R.string.tool_card_truncated, text.length)

    private fun durationText(ms: Long): String =
        if (ms < 1000) "${ms}ms" else String.format(Locale.US, "%.1fs", ms / 1000.0)

    private companion object {
        /** 单段兜底上限，正常工具返回远小于此值。 */
        const val MAX_CHARS = 20_000
    }
}
