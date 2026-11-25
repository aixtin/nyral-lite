package io.github.aixtin.nyrallite

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.AttachmentRef

/**
 * 用户气泡：正文 + 附件缩略图（两列网格）。
 * 纯图片消息隐藏文本气泡；缩略图点击进全屏查看。
 */
internal class UserMessageHolder(v: View) : RecyclerView.ViewHolder(v) {

    private val text: TextView = v.findViewById(R.id.user_text)
    private val attachments: LinearLayout = v.findViewById(R.id.user_attachments)

    fun bind(
        msg: Message,
        open: (AttachmentRef) -> ByteArray?,
        onClick: (AttachmentRef) -> Unit,
    ) {
        text.text = msg.content
        text.visibility = if (msg.content.isBlank()) View.GONE else View.VISIBLE
        attachments.removeAllViews()
        attachments.visibility = if (msg.attachments.isEmpty()) View.GONE else View.VISIBLE
        if (msg.attachments.isEmpty()) return

        val ctx = itemView.context
        val density = ctx.resources.displayMetrics.density
        val size = (THUMB_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()

        msg.attachments.chunked(2).forEach { row ->
            val rowView = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { ref ->
                val image = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginStart = if (rowView.childCount > 0) gap else 0
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_thumb)
                    clipToOutline = true
                    tag = ref.key
                    setOnClickListener { onClick(ref) }
                }
                rowView.addView(image)
                AttachmentImages.load(ref, open, maxPx = size) { bitmap ->
                    // 列表复用后 tag 会变，丢弃过期的解码结果
                    if (image.tag == ref.key) bitmap?.let { image.setImageBitmap(it) }
                }
            }
            attachments.addView(
                rowView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = if (attachments.childCount > 0) gap else 0 },
            )
        }
    }

    private companion object {
        const val THUMB_DP = 150
        const val GAP_DP = 4
    }
}
