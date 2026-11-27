package io.github.aixtin.nyrallite

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.aixtin.nyrallite.kernel.PanelField
import io.github.aixtin.nyrallite.kernel.PanelSpec
import io.github.aixtin.nyrallite.kernel.PluginConfig

/**
 * 按 [PanelSpec] 渲染配置表单（宿主统一 UI）。插件只提供字段描述，不参与渲染。
 * 保存时把控件值统一转成字符串写回该插件的配置命名空间。
 */
internal class PluginPanelView(
    private val activity: AppCompatActivity,
    private val spec: PanelSpec,
    private val config: PluginConfig,
) {
    /** 字段 key 与「读取当前控件值」的函数，顺序与 spec 一致。 */
    private val readers = ArrayList<Pair<String, () -> String>>()

    val root: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), dp(4))
    }

    init {
        for (field in spec.fields) root.addView(build(field))
    }

    /** 把表单当前值写进配置；开关与下拉都已转成字符串。 */
    fun save() {
        for ((key, read) in readers) config.put(key, read())
    }

    private fun build(field: PanelField): LinearLayout {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        }
        when (field) {
            is PanelField.Text -> {
                box.addView(label(field.label))
                box.addView(input(field.key, field.placeholder, field.default, secret = false))
            }
            is PanelField.Secret -> {
                box.addView(label(field.label))
                val edit = input(field.key, field.placeholder, field.default, secret = true)
                box.addView(edit)
                box.addView(showToggle(edit))
            }
            is PanelField.Switch -> {
                val sw = MaterialSwitch(activity).apply {
                    text = field.label
                    textSize = 14f
                    isChecked = readValue(field.key, field.default.toString()).toBoolean()
                }
                readers.add(field.key to { sw.isChecked.toString() })
                box.addView(sw)
            }
            is PanelField.Select -> {
                box.addView(label(field.label))
                box.addView(select(field))
            }
        }
        if (field.description.isNotBlank()) box.addView(desc(field.description))
        return box
    }

    private fun input(key: String, placeholder: String, default: String, secret: Boolean): EditText =
        EditText(activity).apply {
            setSingleLine()
            textSize = 14f
            hint = placeholder
            setText(readValue(key, default))
            // 统一圆角输入框，去掉系统默认下划线样式
            background = ContextCompat.getDrawable(activity, R.drawable.bg_field)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL)
            if (secret) transformationMethod = PasswordTransformationMethod.getInstance()
            readers.add(key to { text?.toString() ?: "" })
        }

    /** 与宿主其它开关保持一致，避免面板里混入原生样式。 */
    private fun showToggle(edit: EditText): MaterialSwitch =
        MaterialSwitch(activity).apply {
            text = "显示明文"
            textSize = 12f
            setOnCheckedChangeListener { _, checked ->
                edit.transformationMethod =
                    if (checked) null else PasswordTransformationMethod.getInstance()
            }
        }

    private fun select(field: PanelField.Select): Spinner =
        Spinner(activity).apply {
            background = ContextCompat.getDrawable(activity, R.drawable.bg_field)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            SpinnerStyle.install(this, field.options.map { it.label })
            val stored = readValue(field.key, field.default)
            setSelection(field.options.indexOfFirst { it.value == stored }.coerceAtLeast(0))
            readers.add(field.key to {
                field.options.getOrNull(selectedItemPosition)?.value ?: field.default
            })
        }

    private fun label(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
    }

    private fun desc(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 11f
        alpha = 0.55f
        setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
    }

    /** 配置里没存过就回落到字段默认值。 */
    private fun readValue(key: String, default: String): String = config.getString(key, default)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
