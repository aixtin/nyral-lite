package io.github.aixtin.nyrallite

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.RenderSegment

/** AI 气泡的视图引用与渲染状态；思考区状态由 [ThinkingRow] 自己持有。 */
internal class AiMessageHolder(v: View) : RecyclerView.ViewHolder(v) {

    val bubble: ViewGroup = v.findViewById(R.id.bubble)
    val container: LinearLayout = v.findViewById(R.id.ai_container)
    val row: LinearLayout = v.findViewById(R.id.thinking_row)
    val label: TextSwitcher = v.findViewById(R.id.thinking_label)
    val count: TextView = v.findViewById(R.id.thinking_count)
    val arrow: ImageView = v.findViewById(R.id.thinking_arrow)
    val scroll: MaxHeightScrollView = v.findViewById(R.id.thinking_scroll)
    val text: TextView = v.findViewById(R.id.thinking_text)

    /** 装配思考摘要栏：单行片段样式 + 180ms 淡入淡出（片段轮播用）。 */
    fun setupThinkingLabel() {
        label.setFactory {
            TextView(label.context).apply {
                textSize = 12f
                setTextColor(context.getColor(R.color.text_hint))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        label.inAnimation = AlphaAnimation(0f, 1f).apply { duration = 180L }
        label.outAnimation = AlphaAnimation(1f, 0f).apply { duration = 180L }
    }

    /** 在 onCreateViewHolder 中装配（需要适配器的 handler 与切换回调）。 */
    lateinit var thinking: ThinkingRow

    /** 已渲染的分段，用于流式增量 diff。 */
    val segments: MutableList<RenderSegment> = ArrayList()
}
