package io.github.aixtin.nyrallite.plugins.markdown

import org.commonmark.ext.gfm.tables.TableBlock

/**
 * 渲染插件内部的分段模型：主体看不到这些类型，只拿到不透明的 stableKey。
 */
internal sealed interface MarkdownSegment {
    val stableKey: String

    data class Text(val text: String) : MarkdownSegment {
        override val stableKey: String get() = "T:${text.length}:${text.hashCode()}"
    }

    data class Table(val block: TableBlock, val sourceKey: String) : MarkdownSegment {
        override val stableKey: String get() = "TABLE:$sourceKey"
    }
}
