package io.github.aixtin.nyrallite.plugins.markdown

import android.content.Context

/** 插件内共用的 dp 换算。 */
internal object Dimens {
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
