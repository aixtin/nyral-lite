package io.github.aixtin.nyrallite

import android.widget.TextView
import io.github.aixtin.nyrallite.contract.ContentRenderer
import io.github.aixtin.nyrallite.contract.RenderSegment

/** AI 消息的段落渲染：全量绑定与流式增量 diff。 */
internal class AiSegmentRenderer(
    private val renderer: ContentRenderer,
    private val onRetry: (Int) -> Unit,
) {

    /** 全量渲染（onBindViewHolder / 首次渲染 / 强制刷新）。 */
    fun renderFull(msg: Message, h: AiMessageHolder, isLast: Boolean) {
        h.segments.clear()
        h.container.removeAllViews()
        h.thinking.reset()
        if (msg.role == Message.ROLE_ERROR && isLast) {
            h.itemView.setOnClickListener { onRetry(h.bindingAdapterPosition) }
        } else {
            h.itemView.setOnClickListener(null)
        }
        if (msg.role == Message.ROLE_ERROR) {
            val tv = TextView(h.container.context).apply {
                setTextColor(0xFFD32F2F.toInt())
                textSize = 15f
                text = msg.content
            }
            h.container.addView(tv)
            return
        }
        h.thinking.updateThinking(msg)
        val segs = renderer.segments(msg.content)
        for (seg in segs) {
            val view = renderer.renderSegment(h.container.context, seg, msg.content, msg.streaming)
            h.container.addView(view)
            h.segments.add(seg)
        }
    }

    /**
     * 主线程：用已 parse 好的 segments 做增量 diff + 渲染。
     * 找到公共前缀 common，只重渲染末尾变化段；段落/表格原地复用 View。
     */
    fun applySegments(
        h: AiMessageHolder,
        msg: Message,
        fullContent: String,
        newSegs: List<RenderSegment>,
    ) {
        h.thinking.updateThinking(msg)
        val oldSegs = h.segments

        var common = 0
        val minSize = minOf(oldSegs.size, newSegs.size)
        while (common < minSize && oldSegs[common].stableKey == newSegs[common].stableKey) {
            common++
        }

        if (common == oldSegs.size && newSegs.size == oldSegs.size) return

        // 原地更新: 段落复用 TextView, 表格复用 TableLayout 且只改变化的 cell,
        // 避免每帧销毁/重建末尾整段（大表格时这是主要卡顿源）。
        val ctx = h.container.context
        for (i in common until newSegs.size) {
            val existing = h.container.getChildAt(i)
            val seg = newSegs[i]
            if (existing != null) {
                if (renderer.updateSegmentView(ctx, existing, seg, fullContent, msg.streaming)) continue
                h.container.removeViewAt(i)
            }
            h.container.addView(renderer.renderSegment(ctx, seg, fullContent, msg.streaming), i)
        }
        while (h.container.childCount > newSegs.size) {
            h.container.removeViewAt(h.container.childCount - 1)
        }
        h.segments.clear()
        h.segments.addAll(newSegs)
    }
}
