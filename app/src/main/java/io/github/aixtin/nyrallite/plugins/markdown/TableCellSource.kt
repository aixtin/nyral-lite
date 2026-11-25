package io.github.aixtin.nyrallite.plugins.markdown

import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.renderer.text.TextContentRenderer

/** 从表格 cell 提取原始 markdown 文本。 */
internal object TableCellSource {

    /** 不含 | 分隔符，交给 Markwon 渲染内联格式。 */
    fun of(cell: TableCell, lines: List<String>): String {
        val spans = cell.sourceSpans
        if (spans.isNotEmpty()) {
            val sb = StringBuilder()
            for (span in spans) {
                val line = lines.getOrNull(span.lineIndex) ?: continue
                val col = span.columnIndex
                val len = span.length
                if (col <= line.length) {
                    sb.append(line.substring(col, minOf(col + len, line.length)))
                }
            }
            val src = sb.toString().trim()
            if (src.isNotEmpty()) return src
        }
        // 兜底：sourceSpans 为空时（如流式中未闭合的表格），从节点树直接提取纯文本
        return TextContentRenderer.builder().build().render(cell).trim()
    }
}
