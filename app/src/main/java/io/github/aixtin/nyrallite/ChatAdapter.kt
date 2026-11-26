package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ContentRenderer

/**
 * 聊天列表适配器。
 *
 * 流式更新不走 notifyItemChanged（整项重绑 + 动画会闪），而是直接找可见 holder
 * 做增量渲染；被回收的项下次 onBind 用最新文本全量渲染。因为只有一个渲染引擎，
 * 两者结果天然一致，不会跳变。
 *
 * 职责边界：
 *  - 分段解析调度 → [IncrementalParseScheduler]
 *  - 思考区三态/抽屉/动画 → [ThinkingRow]
 *  - ViewHolder 视图引用 → [AiMessageHolder] / [UserMessageHolder]
 * 本类只负责绑定、diff 与原地更新。
 */
class ChatAdapter(
    private val items: MutableList<Message>,
    private val renderer: ContentRenderer,
    private val onRetry: (Int) -> Unit,
    /** 附件字节读取：由宿主注入 AttachmentStores。 */
    private val openAttachment: (AttachmentRef) -> ByteArray? = { null },
    /** 点击用户消息里的图片：进全屏查看。 */
    private val onAttachmentClick: (AttachmentRef) -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rv: RecyclerView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val parseScheduler = IncrementalParseScheduler(renderer)
    private val segmentRenderer = AiSegmentRenderer(renderer, onRetry)

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
        const val TYPE_TOOL = 2
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        rv = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        parseScheduler.dispose()
        rv = null
    }

    override fun getItemViewType(position: Int): Int = when (items[position].role) {
        Message.ROLE_USER -> TYPE_USER
        Message.ROLE_TOOL -> TYPE_TOOL
        else -> TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserMessageHolder(inflater.inflate(R.layout.item_user, parent, false))
            TYPE_TOOL -> ToolCallHolder(
                inflater.inflate(R.layout.item_tool_call, parent, false),
            ) { toggleToolCard(it) }
            else -> AiMessageHolder(inflater.inflate(R.layout.item_ai, parent, false)).also { holder ->
                holder.thinking = ThinkingRow(holder, handler) { toggleDrawerAt(holder) }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserMessageHolder -> holder.bind(msg, openAttachment, onAttachmentClick)
            is ToolCallHolder -> holder.bind(msg.toolCard)
            is AiMessageHolder -> segmentRenderer.renderFull(msg, holder, position == items.lastIndex)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * 流式增量更新。parse 在后台线程完成（见 [IncrementalParseScheduler]）。
     * 返回 true 表示当前末尾段是重段（表格），调用方据此降到 30fps。
     */
    fun updateAi(
        position: Int,
        content: String,
        thinking: String,
        onApplied: (() -> Unit)? = null,
    ): Boolean {
        if (position < 0 || position >= items.size) return false
        val msg = items[position]
        msg.content = content
        msg.thinking = thinking
        val holder = rv?.findViewHolderForAdapterPosition(position) as? AiMessageHolder
            ?: return false
        parseScheduler.request(snapshot = { msg.content }) { text, segments ->
            val pos = holder.bindingAdapterPosition
            val stillValid = pos != RecyclerView.NO_POSITION &&
                pos in items.indices &&
                items[pos] === msg &&
                msg.role == Message.ROLE_AI
            if (!stillValid) return@request false
            segmentRenderer.applySegments(holder, msg, text, segments)
            onApplied?.invoke()
            true
        }
        return holder.segments.lastOrNull()?.let(renderer::isHeavySegment) ?: false
    }

    /**
     * 只有思考增量、正文还没开始时使用：直接把思考写入消息并刷新思考行，
     * 不重解析正文（此时正文为空，解析纯属浪费）。
     */
    fun updateThinking(position: Int, thinking: String) {
        if (position < 0 || position >= items.size) return
        val msg = items[position]
        msg.thinking = thinking
        val holder = rv?.findViewHolderForAdapterPosition(position) as? AiMessageHolder
        holder?.thinking?.updateThinking(msg)
    }

    /** 工具卡片点击：切换参数/结果展开。 */
    private fun toggleToolCard(h: ToolCallHolder) {
        val pos = h.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION || pos !in items.indices) return
        val card = items[pos].toolCard ?: return
        card.expanded = !card.expanded
        notifyItemChanged(pos)
    }

    /** 思考栏点击：解析当前绑定的消息，流式中忽略。 */
    private fun toggleDrawerAt(h: AiMessageHolder) {
        val pos = h.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION || pos !in items.indices) return
        val msg = items[pos]
        if (msg.streaming) return
        ThinkingDrawer.toggle(msg, h, rv)
    }
}
