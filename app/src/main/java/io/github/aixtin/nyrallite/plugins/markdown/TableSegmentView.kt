package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TextView
import io.github.aixtin.nyrallite.R
import org.commonmark.ext.gfm.tables.TableBlock

/**
 * 表格段容器：HorizontalScrollView + 原生 TableLayout。
 * 列宽由内容决定（稳定不抖），整体可横滑；行/单元格复用逻辑在 [TableFiller]。
 */
internal object TableSegmentView {

    /** 标记"这个 View 是表格容器"，供原地更新判定。 */
    val MARK = Any()

    fun create(context: Context, block: TableBlock, fullText: String, streaming: Boolean): View {
        val hsv = HorizontalScrollView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            tag = MARK
        }
        val table = TableLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            isStretchAllColumns = false
        }
        hsv.addView(table)
        // 新建时不做末行压暗；压暗只在流式更新路径生效（与原实现保持一致）
        TableFiller.fill(context, hsv, block, fullText, dimLastRow = false)
        return hsv
    }

    fun newCell(context: Context): TextView {
        val pad = Dimens.dp(context, 9)
        val padV = Dimens.dp(context, 6)
        return TextView(context).apply {
            setTextColor(0xFF1A1A1A.toInt())
            textSize = 14f
            setPadding(pad, padV, pad, padV)
            setBackgroundResource(R.drawable.bg_cell)
        }
    }

    fun newHintCell(context: Context): TextView {
        val pad = Dimens.dp(context, 9)
        val padV = Dimens.dp(context, 6)
        return TextView(context).apply {
            setTextColor(0xFF999999.toInt())
            textSize = 12f
            setPadding(pad, padV, pad, padV)
        }
    }
}
