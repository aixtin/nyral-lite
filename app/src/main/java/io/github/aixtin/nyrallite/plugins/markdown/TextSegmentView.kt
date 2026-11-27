package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context
import io.github.aixtin.nyrallite.R
import android.view.View
import android.widget.TextView

/** 普通段落段：一个 TextView，内容变化时原地重渲染。 */
internal object TextSegmentView {

    fun create(context: Context, text: String): TextView = TextView(context).apply {
        setTextColor(context.getColor(R.color.ai_text))
        textSize = 15f
        setLineSpacing(Dimens.dp(context, 3).toFloat(), 1f)
        MarkdownEngine.render(this, text)
        visibility = visibilityOf(text)
    }

    fun update(view: TextView, text: String) {
        view.visibility = visibilityOf(text)
        MarkdownEngine.render(view, text)
    }

    /** 流式初期只收到 ```lang 这一行时，先隐藏，避免渲染出一块空的代码底色。 */
    private fun visibilityOf(text: String): Int =
        if (FENCE_ONLY.matches(text.trim())) View.GONE else View.VISIBLE

    private val FENCE_ONLY = Regex("^(`{3,}|~{3,})[^\\n]*$")
}
