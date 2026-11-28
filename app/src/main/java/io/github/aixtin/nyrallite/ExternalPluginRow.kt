package io.github.aixtin.nyrallite

import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.plugins.dex.ExternalPluginRecord

/** 外部插件的一行卡片：名称、版本、移除按钮、启停开关与状态行。 */
internal class ExternalPluginRow(
    private val activity: AppCompatActivity,
    private val onRemove: (ExternalPluginRecord) -> Unit = {},
    private val onToggle: (ExternalPluginRecord, Boolean, SwitchCompat) -> Unit = { _, _, _ -> },
) {

    /** WASM 插件的一行卡片：与外部插件卡片同一套视觉（名称加粗 + 胶囊移除 + 状态行）。 */
    fun wasm(id: String, onRemove: () -> Unit): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(activity, R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = id
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
                    addView(chip("移除", R.color.tool_fail) { onRemove() })
                },
            )
            addView(
                TextView(activity).apply {
                    text = "运行中"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(activity, R.color.brand))
                    alpha = 0.75f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4) }
                },
            )
        }

    /** JS 工具的一行卡片：与 WASM 行同一套视觉，多一个「运行」胶囊（按需执行，跑完即焚）。 */
    fun jsTool(id: String, onRun: () -> Unit, onRemove: () -> Unit): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(activity, R.drawable.bg_card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = id
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
                    addView(chip("运行", R.color.brand) { onRun() })
                    addView(chip("移除", R.color.tool_fail) { onRemove() })
                },
            )
            addView(
                TextView(activity).apply {
                    text = "按需执行，跑完即焚"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(activity, R.color.brand))
                    alpha = 0.75f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4) }
                },
            )
        }

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
                addView(chip("设置", R.color.brand) { onConfigure() })
            }
            addView(chip("移除", R.color.tool_fail) { onRemove(record) })
            addView(
                SwitchCompat(activity).apply {
                    isChecked = record.enabled
                    setOnCheckedChangeListener { _, checked -> onToggle(record, checked, this) }
                },
            )
        }

    /** 轻量胶囊按钮：透明底 + 淡描边，与底部工具条同一套视觉。 */
    private fun chip(text: String, colorRes: Int, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, colorRes))
            background = ContextCompat.getDrawable(activity, R.drawable.bg_bar_item)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) }
            setOnClickListener { onClick() }
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
