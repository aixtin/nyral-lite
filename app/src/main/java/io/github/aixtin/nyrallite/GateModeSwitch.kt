package io.github.aixtin.nyrallite

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore

/**
 * 输入栏"门禁"切换器：单击按 自动 → 严格 → 放行 → 自动 循环切换并落盘，
 * 切换后用轻提示说明当前档位含义；工具条上直接显示当前档位。
 */
class GateModeSwitch(
    private val context: Context,
    private val item: TextView,
    private val store: SecuritySettingsStore,
) {

    /** 刷新工具条文案；从配置页返回时也调用，保持与配置页一致。 */
    fun refresh() {
        item.text = context.getString(R.string.bar_gate_mode, label(store.load().mode))
    }

    /** 单击：切到下一档并轻提示。 */
    fun toggle() {
        val settings = store.load()
        val next = when (settings.mode) {
            SecurityMode.AUTO -> SecurityMode.STRICT
            SecurityMode.STRICT -> SecurityMode.OFF
            SecurityMode.OFF -> SecurityMode.AUTO
        }
        store.save(settings.copy(mode = next))
        refresh()
        // 关闭门禁风险更高，提示停留久一点
        val duration = if (next == SecurityMode.OFF) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, toastRes(next), duration).show()
    }

    private fun label(mode: SecurityMode): String = context.getString(
        when (mode) {
            SecurityMode.AUTO -> R.string.gate_label_auto
            SecurityMode.STRICT -> R.string.gate_label_strict
            SecurityMode.OFF -> R.string.gate_label_off
        },
    )

    private fun toastRes(mode: SecurityMode): Int = when (mode) {
        SecurityMode.AUTO -> R.string.gate_toast_auto
        SecurityMode.STRICT -> R.string.gate_toast_strict
        SecurityMode.OFF -> R.string.gate_toast_off
    }
}
