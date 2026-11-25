package io.github.aixtin.nyrallite

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.RenderSegment

/** AI 气泡的视图引用与渲染状态；思考区状态由 [ThinkingRow] 自己持有。 */
internal class AiMessageHolder(v: View) : RecyclerView.ViewHolder(v) {

    val bubble: ViewGroup = v.findViewById(R.id.bubble)
    val container: LinearLayout = v.findViewById(R.id.ai_container)
    val row: LinearLayout = v.findViewById(R.id.thinking_row)
    val label: TextView = v.findViewById(R.id.thinking_label)
    val arrow: ImageView = v.findViewById(R.id.thinking_arrow)
    val scroll: MaxHeightScrollView = v.findViewById(R.id.thinking_scroll)
    val text: TextView = v.findViewById(R.id.thinking_text)

    /** 在 onCreateViewHolder 中装配（需要适配器的 handler 与切换回调）。 */
    lateinit var thinking: ThinkingRow

    /** 已渲染的分段，用于流式增量 diff。 */
    val segments: MutableList<RenderSegment> = ArrayList()
}
