package io.github.aixtin.nyrallite

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/**
 * 下拉选择统一样式：收起/展开用同一套文字布局，弹层为圆角白卡，
 * 选中项品牌色加粗。门禁档位与插件配置面板共用。
 */
internal object SpinnerStyle {

    fun install(spinner: Spinner, labels: List<String>) {
        spinner.adapter = object : ArrayAdapter<String>(
            spinner.context,
            R.layout.spinner_text_item,
            labels,
        ) {
            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View = super.getDropDownView(position, convertView, parent).also { row ->
                val selected = position == spinner.selectedItemPosition
                (row as TextView).apply {
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (selected) R.color.brand else R.color.ai_text,
                        ),
                    )
                    setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                }
            }
        }.apply { setDropDownViewResource(R.layout.spinner_text_dropdown) }
        spinner.setPopupBackgroundDrawable(
            ContextCompat.getDrawable(spinner.context, R.drawable.bg_spinner_popup),
        )
    }
}
