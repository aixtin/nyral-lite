package io.github.aixtin.nyrallite

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.aixtin.nyrallite.contract.AttachmentRef

/**
 * 输入区上方的待发送缩略图条：横向滚动，每张右上角有删除按钮。
 * 只负责展示与删除，图片的选取与转码在 MainActivity。
 */
internal class ComposerAttachments(
    private val scroll: View,
    private val strip: LinearLayout,
    private val open: (AttachmentRef) -> ByteArray?,
    private val onRemove: (Int) -> Unit,
) {
    fun render(refs: List<AttachmentRef>) {
        strip.removeAllViews()
        scroll.visibility = if (refs.isEmpty()) View.GONE else View.VISIBLE
        if (refs.isEmpty()) return

        val density = strip.resources.displayMetrics.density
        val size = (THUMB_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()

        refs.forEachIndexed { index, ref ->
            val frame = FrameLayout(strip.context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            }
            val image = ImageView(strip.context).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_thumb)
                clipToOutline = true
                tag = ref.key
            }
            frame.addView(image)
            frame.addView(removeButton(density, index))
            strip.addView(frame)
            AttachmentImages.load(ref, open, maxPx = size) { bitmap ->
                if (image.tag == ref.key) bitmap?.let { image.setImageBitmap(it) }
            }
        }
    }

    private fun removeButton(density: Float, index: Int): TextView {
        val size = (20 * density).toInt()
        return TextView(strip.context).apply {
            text = "×"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_thumb_close)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (2 * density).toInt()
                marginEnd = (2 * density).toInt()
            }
            setOnClickListener { onRemove(index) }
        }
    }

    private companion object {
        const val THUMB_DP = 56
        const val GAP_DP = 6
    }
}
