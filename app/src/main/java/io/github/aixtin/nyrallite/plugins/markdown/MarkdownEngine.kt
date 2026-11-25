package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

/**
 * 全 App 唯一的 Markwon 实例。
 * 不做协议前缀剥离、不做占位卡、不做多套收尾分支——只有 setMarkdown。
 */
// prism4j-bundler 扫描此注解, 在同包生成 GrammarLocatorDef (包含全部支持的语言)。
@PrismBundle(includeAll = true, grammarLocatorClassName = ".GrammarLocatorDef")
internal object MarkdownEngine {

    @Volatile
    private var markwon: Markwon? = null

    fun get(ctx: Context): Markwon = markwon ?: synchronized(this) {
        // 表格由 TableSegmentView 自绘(可横滑), 这里不再用 TablePlugin, 避免重复渲染。
        markwon ?: Markwon.builder(ctx.applicationContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(
                SyntaxHighlightPlugin.create(
                    Prism4j(GrammarLocatorDef()),
                    Prism4jThemeDefault.create(),
                ),
            )
            .build()
            .also { markwon = it }
    }

    /** 渲染到 TextView：流式前缀和收尾都走这里。 */
    fun render(textView: TextView, text: String) {
        get(textView.context).setMarkdown(textView, text)
    }
}
