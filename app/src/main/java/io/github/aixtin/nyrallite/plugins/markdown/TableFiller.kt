package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead

/**
 * 表格填充/更新：行、单元格全部复用，只重渲染文本真正变化的 cell。
 * 全文只 split 一次行，避免每个 cell 切一遍的分配热点。
 *
 * 超过 MAX_TABLE_ROWS / MAX_TABLE_COLS 的部分不建 View，用一行提示代替，
 * 避免超大表格把主线程 measure/layout 拖到 ANR。
 */
internal object TableFiller {

    private const val MAX_TABLE_ROWS = 100
    private const val MAX_TABLE_COLS = 10

    /** 流式中最后一行数据行的透明度 */
    private const val DIM_ROW_ALPHA = 0.6f

    /**
     * dimLastRow（流式）：最后一行数据压暗到 60%。
     * 既标记"这行还在写"，也掩盖 cell 更新与列表滚动同帧合成时的残影。
     */
    fun fill(
        context: Context,
        hsv: HorizontalScrollView,
        block: TableBlock,
        fullText: String,
        dimLastRow: Boolean,
    ) {
        val table = hsv.getChildAt(0) as? TableLayout ?: return
        val lines = fullText.split("\n")
        val shape = TableScanner.scan(block, MAX_TABLE_ROWS, MAX_TABLE_COLS)

        var rowIndex = 0
        var dataRendered = 0
        var section = block.firstChild
        while (section != null) {
            val isHead = section is TableHead
            if (!isHead && dataRendered >= MAX_TABLE_ROWS) break
            var row = section.firstChild
            while (row != null) {
                if (!isHead && dataRendered >= MAX_TABLE_ROWS) break
                val tr = (table.getChildAt(rowIndex) as? TableRow)
                    ?: TableRow(context).also { it.alpha = 0f; table.addView(it) }
                var cellIndex = 0
                var cell = row.firstChild
                while (cell != null && cellIndex < shape.colsToRender) {
                    if (cell is TableCell) {
                        val src = TableCellSource.of(cell, lines).ifBlank { " " }
                        val tv = (tr.getChildAt(cellIndex) as? TextView)
                            ?: TableSegmentView.newCell(context).also { tr.addView(it) }
                        if (tv.tag != src) {
                            MarkdownEngine.render(tv, src)
                            tv.tag = src
                        }
                        cellIndex++
                    }
                    cell = cell.next
                }
                // 内容收缩时清掉多余的 cell
                while (tr.childCount > cellIndex) tr.removeViewAt(tr.childCount - 1)
                rowIndex++
                if (!isHead) dataRendered++
                row = row.next
            }
            section = section.next
        }

        // 超限提示行：固定在最后一行，流式增长时原地更新文案
        if (shape.truncated) {
            val tr = (table.getChildAt(rowIndex) as? TableRow)
                ?: TableRow(context).also { table.addView(it) }
            val tv = (tr.getChildAt(0) as? TextView)
                ?: TableSegmentView.newHintCell(context).also { tr.addView(it) }
            while (tr.childCount > 1) tr.removeViewAt(tr.childCount - 1)
            val hint = truncationHint(shape)
            val hintTag = "HINT:$hint"
            if (tv.tag != hintTag) {
                tv.text = hint
                tv.tag = hintTag
                // 可能复用到原来的数据 cell，统一重置成灰色小字样式
                tv.background = null
                tv.setTextColor(0xFF999999.toInt())
                tv.textSize = 12f
            }
            rowIndex++
        }

        // 内容收缩时清掉多余的行
        while (table.childCount > rowIndex) table.removeViewAt(table.childCount - 1)

        // 最后一行数据压暗（流式进行中）；新建行从透明过渡到目标透明度
        if (table.childCount > 0) {
            val lastDataIdx = if (shape.truncated) rowIndex - 2 else rowIndex - 1
            for (i in 0 until table.childCount) {
                val row = table.getChildAt(i)
                val target = if (dimLastRow && i == lastDataIdx) DIM_ROW_ALPHA else 1f
                if (row.alpha != target) {
                    if (row.alpha == 0f) {
                        row.animate().alpha(target).setDuration(140).start()
                    } else {
                        row.alpha = target
                    }
                }
            }
        }
    }

    private fun truncationHint(shape: TableShape): String = when {
        shape.dataRows > shape.dataRowsToRender && shape.maxCols > shape.colsToRender ->
            "… 表格过大，仅显示前 ${shape.dataRowsToRender} 行数据 × ${shape.colsToRender} 列" +
                "（原表 ${shape.dataRows} 行 × ${shape.maxCols} 列）"
        shape.dataRows > shape.dataRowsToRender ->
            "… 仅显示前 ${shape.dataRowsToRender} 行数据（共 ${shape.dataRows} 行）"
        else -> "… 仅显示前 ${shape.colsToRender} 列（共 ${shape.maxCols} 列）"
    }
}
