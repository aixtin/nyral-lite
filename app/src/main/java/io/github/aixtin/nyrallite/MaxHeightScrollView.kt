package io.github.aixtin.nyrallite

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 限高 ScrollView：内容超过 maxHeight 时高度封顶并内部滚动，不够高时 wrap_content 自适应。
 * 用于思考抽屉——几千字的思考内容不会把气泡撑得无限长。
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {

    var maxHeightPx: Int = dp(200)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, spec)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
