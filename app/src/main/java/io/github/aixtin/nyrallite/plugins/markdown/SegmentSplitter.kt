package io.github.aixtin.nyrallite.plugins.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * 用 commonmark 的 sourceSpans 把原文切成 文本段 / 表格段。
 * 增量 diff 的基础：同一段内容不变，stableKey 就不变。
 */
internal object SegmentSplitter {

    fun split(parser: Parser, text: String): List<MarkdownSegment> {
        if (text.isBlank()) return emptyList()
        return splitDocument(parser.parse(text), text)
    }

    private fun splitDocument(document: Node, fullText: String): List<MarkdownSegment> {
        val lineToTable = HashMap<Int, TableBlock>()
        val tableLineRange = HashMap<TableBlock, Pair<Int, Int>>()
        var child = document.firstChild
        while (child != null) {
            if (child is TableBlock) {
                val spans = child.sourceSpans
                if (spans.isNotEmpty()) {
                    val start = spans.minOf { it.lineIndex }
                    val end = spans.maxOf { it.lineIndex }
                    tableLineRange[child] = start to end
                    for (i in start..end) lineToTable[i] = child
                }
            }
            child = child.next
        }

        val lines = fullText.split("\n")
        val result = ArrayList<MarkdownSegment>()
        var i = 0
        while (i < lines.size) {
            val table = lineToTable[i]
            if (table != null) {
                val range = tableLineRange[table]!!
                val src = lines.subList(range.first, range.second + 1).joinToString("\n")
                while (i <= range.second) i++
                result.add(MarkdownSegment.Table(table, src))
            } else {
                val sb = StringBuilder()
                while (i < lines.size && lineToTable[i] == null) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i])
                    i++
                }
                result.add(MarkdownSegment.Text(sb.toString()))
            }
        }
        return result
    }
}
