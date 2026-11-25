package io.github.aixtin.nyrallite.plugins.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead

/** 表格的行列统计结果。 */
internal data class TableShape(
    val dataRows: Int,
    val maxCols: Int,
    val dataRowsToRender: Int,
    val colsToRender: Int,
    val truncated: Boolean,
)

/** 只遍历节点统计行列，不建任何 View；表头不计入数据行。 */
internal object TableScanner {

    fun scan(block: TableBlock, maxRows: Int, maxCols: Int): TableShape {
        var dataRows = 0
        var foundMaxCols = 0
        var section = block.firstChild
        while (section != null) {
            val isHead = section is TableHead
            var row = section.firstChild
            while (row != null) {
                var cells = 0
                var c = row.firstChild
                while (c != null) {
                    if (c is TableCell) cells++
                    c = c.next
                }
                if (!isHead) dataRows++
                if (cells > foundMaxCols) foundMaxCols = cells
                row = row.next
            }
            section = section.next
        }
        val dataRowsToRender = minOf(dataRows, maxRows)
        val colsToRender = minOf(foundMaxCols, maxCols)
        return TableShape(
            dataRows = dataRows,
            maxCols = foundMaxCols,
            dataRowsToRender = dataRowsToRender,
            colsToRender = colsToRender,
            truncated = dataRows > dataRowsToRender || foundMaxCols > colsToRender,
        )
    }
}
