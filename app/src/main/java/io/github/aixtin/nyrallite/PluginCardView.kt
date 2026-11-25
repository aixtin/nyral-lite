package io.github.aixtin.nyrallite

import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.PluginManifest

/**
 * 插件状态卡片：只读展示清单信息（名称/id/版本/内置标记/权限/依赖）。
 * 安全插件额外显示门禁档位，MCP 客户端显示服务器状态；[onOpenConfig] 非空时整卡可点跳配置。
 */
class PluginCardView(
    context: Context,
    private val manifest: PluginManifest,
    installed: Boolean,
    gateMode: SecurityMode?,
    extraLine: String? = null,
    openLabelRes: Int = R.string.plugins_open_config,
    onOpenConfig: (() -> Unit)? = null,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_card)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }

        addView(headerRow(installed))
        addView(line("${manifest.id} · v${manifest.version}", size = 12f, alpha = 0.6f))
        addView(line(tags(), size = 12f, alpha = 0.8f))
        if (extraLine != null) {
            addView(line(extraLine, size = 13f, topMargin = 6, alpha = 0.8f))
        }
        if (gateMode != null) {
            addView(
                line(
                    "${context.getString(R.string.plugins_gate)}：${modeText(gateMode)}",
                    size = 13f,
                    topMargin = 6,
                ),
            )
        }
        if (onOpenConfig != null) {
            addView(
                line(
                    context.getString(openLabelRes),
                    size = 12f,
                    color = R.color.brand,
                    topMargin = 6,
                ),
            )
            setOnClickListener { onOpenConfig() }
        }
    }

    private fun headerRow(installed: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(TextView(context).apply {
            text = nameRes().takeIf { it != 0 }?.let { context.getString(it) } ?: manifest.id
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.ai_text))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = context.getString(if (installed) R.string.plugins_running else R.string.plugins_stopped)
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (installed) R.color.brand else R.color.ai_text,
                ),
            )
            alpha = if (installed) 1f else 0.5f
        })
    }

    private fun tags(): String {
        val parts = mutableListOf<String>()
        if (manifest.builtin) parts += context.getString(R.string.plugins_builtin)
        // 目前没有启停能力，只有安全插件额外标注"不可禁用"这个永久约束
        if (!manifest.disableable) parts += context.getString(R.string.plugins_locked)
        if (manifest.permissions.isNotEmpty()) {
            val names = manifest.permissions.sortedBy { it.ordinal }.joinToString("/") {
                context.getString(permissionRes(it))
            }
            parts += "${context.getString(R.string.plugins_permissions)}：$names"
        }
        if (manifest.dependsOn.isNotEmpty()) {
            parts += "${context.getString(R.string.plugins_depends)}：" +
                manifest.dependsOn.sorted().joinToString(", ")
        }
        return parts.joinToString(" · ")
    }

    private fun line(
        text: String,
        size: Float,
        alpha: Float = 1f,
        color: Int = R.color.ai_text,
        topMargin: Int = 2,
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(ContextCompat.getColor(context, color))
        this.alpha = alpha
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = dp(topMargin)
        }
    }

    private fun nameRes(): Int = when (manifest.id) {
        "storage-prefs" -> R.string.plugin_name_storage
        "model-openai" -> R.string.plugin_name_model
        "render-markdown" -> R.string.plugin_name_markdown
        "security-policy" -> R.string.plugin_name_security
        "mcp-client" -> R.string.plugin_name_mcp
        else -> 0
    }

    private fun modeText(mode: SecurityMode): String = context.getString(
        when (mode) {
            SecurityMode.OFF -> R.string.cfg_security_off
            SecurityMode.AUTO -> R.string.cfg_security_auto
            SecurityMode.STRICT -> R.string.cfg_security_strict
        },
    )

    private fun permissionRes(permission: Permission): Int = when (permission) {
        Permission.NETWORK -> R.string.plugin_perm_network
        Permission.FILES -> R.string.plugin_perm_files
        Permission.CLIPBOARD -> R.string.plugin_perm_clipboard
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
