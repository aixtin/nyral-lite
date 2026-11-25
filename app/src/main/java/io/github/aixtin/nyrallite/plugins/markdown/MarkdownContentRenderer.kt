package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import io.github.aixtin.nyrallite.contract.ContentRenderer
import io.github.aixtin.nyrallite.contract.RenderSegment
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * [ContentRenderer] 实现：普通段落走 TextView + Markwon，表格走自绘可横滑容器。
 * 分段解析与 View 构造分离，主体只接触不透明的 [RenderSegment]。
 */
class MarkdownContentRenderer : ContentRenderer {

    private val parser: Parser by lazy {
        Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .extensions(listOf(TablesExtension.create()))
            .build()
    }

    override fun segments(text: String): List<RenderSegment> =
        SegmentSplitter.split(parser, text).map {
            RenderSegment(stableKey = it.stableKey, payload = it)
        }

    override fun renderSegment(
        context: Context,
        segment: RenderSegment,
        fullText: String,
        streaming: Boolean,
    ): View = when (val seg = segment.payload) {
        is MarkdownSegment.Text -> TextSegmentView.create(context, seg.text.trim('\n'))
        is MarkdownSegment.Table -> TableSegmentView.create(context, seg.block, fullText, streaming)
        else -> throw IllegalArgumentException("未知分段类型: ${seg.javaClass.name}")
    }

    override fun updateSegmentView(
        context: Context,
        view: View,
        segment: RenderSegment,
        fullText: String,
        streaming: Boolean,
    ): Boolean {
        return when (val seg = segment.payload) {
            is MarkdownSegment.Text -> {
                if (view !is TextView) return false
                TextSegmentView.update(view, seg.text.trim('\n'))
                true
            }
            is MarkdownSegment.Table -> {
                if (view.tag !== TableSegmentView.MARK) return false
                TableFiller.fill(context, view as HorizontalScrollView, seg.block, fullText, streaming)
                true
            }
            else -> false
        }
    }

    override fun isHeavySegment(segment: RenderSegment): Boolean =
        segment.payload is MarkdownSegment.Table
}
