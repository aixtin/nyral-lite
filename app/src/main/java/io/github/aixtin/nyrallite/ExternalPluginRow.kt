package io.github.aixtin.nyrallite

import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.plugins.dex.ExternalPluginRecord

/** 外部插件的一行卡片：名称、版本、移除按钮、启停开关与状态行。 */
internal class ExternalPluginRow(
    private val activity: AppCompatActivity,
    private val onRemove: (ExternalPluginRecord) -> Unit,
    private val onToggle: (ExternalPluginRecord, Boolean, SwitchCompat) -> Unit,
) {

    fun build(
        record: ExternalPluginRecord,
        installed: Boolean,
        onConfigure: (() -> Unit)?,
    ): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(activity, R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }

            addView(header(record, onConfigure))
            addView(status(record, installed))
        }

    fun hint(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
        alpha = 0.5f
    }

    private fun header(
        record: ExternalPluginRecord,
        onConfigure: (() -> Unit)?,
    ): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                TextView(activity).apply {
                    text = if (record.version.isBlank()) record.id
                           else "${record.id}  v${record.version}"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                },
            )
            if (onConfigure != null) {
                addView(
                    Button(activity).apply {
                        text = "设置"
                        textSize = 12f
                        minWidth = 0
                        minimumWidth = 0
                        setOnClickListener { onConfigure() }
                    },
                )
            }
            addView(
                Button(activity).apply {
                    text = "移除"
                    textSize = 12f
                    minWidth = 0
                    minimumWidth = 0
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    setOnClickListener { onRemove(record) }
                },
            )
            addView(
                SwitchCompat(activity).apply {
                    isChecked = record.enabled
                    setOnCheckedChangeListener { _, checked -> onToggle(record, checked, this) }
                },
            )
        }

    private fun status(record: ExternalPluginRecord, installed: Boolean): TextView {
        val (text, color) = when {
            installed -> "运行中" to R.color.brand
            record.enabled -> "已启用但未加载（加载失败，可尝试重新导入）" to R.color.tool_fail
            else -> "已禁用（APK 保留）" to R.color.ai_text
        }
        return TextView(activity).apply {
            this.text = text
            textSize = 11f
            setTextColor(ContextCompat.getColor(activity, color))
            alpha = 0.75f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
