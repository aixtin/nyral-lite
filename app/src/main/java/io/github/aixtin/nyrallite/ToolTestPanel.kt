package io.github.aixtin.nyrallite

import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.contract.ToolResult
import kotlinx.coroutines.CoroutineScope

/** 一条自测项：工具名 + 测试参数。 */
internal data class TestSpec(val name: String, val args: String)

/**
 * 工具自测面板：每个系统原生工具一行，点击即跑，右侧显示结果。
 * 权限申请与实际调用在 [ToolTestRunner]，这里只管界面。
 */
internal class ToolTestPanel(
    private val activity: AppCompatActivity,
    scope: CoroutineScope,
    private val panel: LinearLayout,
) {
    private val runner = ToolTestRunner(activity, scope) { name, result, inProgress ->
        setResult(name, result, inProgress)
    }

    init {
        for (spec in SPECS) panel.addView(row(spec))
    }

    /** 一行：[ 工具名 chip ] ... [ 结果标签 ] */
    private fun row(spec: TestSpec): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) }
        }
        val chip = TextView(activity).apply {
            text = spec.name
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.bar_action_text))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = ContextCompat.getDrawable(activity, R.drawable.bg_bar_item)
            isClickable = true
            isFocusable = true
            setOnClickListener { runner.run(spec) }
        }
        val resultTv = TextView(activity).apply {
            text = "—"
            textSize = 12f
            setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
            alpha = 0.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { leftMargin = dp(12) }
        }
        // 把结果标签存进 chip 的 tag，执行完按工具名找回
        chip.tag = resultTv
        row.addView(chip)
        row.addView(resultTv)
        return row
    }

    private fun setResult(toolName: String, result: ToolResult, inProgress: Boolean) {
        val chip = (0 until panel.childCount)
            .map { panel.getChildAt(it) as? LinearLayout }
            .firstOrNull { row -> (row?.getChildAt(0) as? TextView)?.text == toolName }
            ?.getChildAt(0) as? TextView ?: return
        val resultTv = chip.tag as? TextView ?: return

        if (inProgress) {
            resultTv.text = "…"
            resultTv.alpha = 0.8f
            resultTv.setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
            return
        }
        resultTv.text = if (result.isError) "✗ ${result.content.take(40)}"
                        else "✓ ${result.content.take(40)}"
        resultTv.setTextColor(
            ContextCompat.getColor(activity, if (result.isError) R.color.tool_fail else R.color.tool_ok),
        )
        resultTv.alpha = 1f
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private companion object {
        /** interval_minutes=1 + run_once=true 让前台任务自动退出，不污染。 */
        val SPECS = listOf(
            TestSpec("send_notification", """{"title":"Nyral 自测","body":"send_notification 链路 OK"}"""),
            TestSpec("dial_phone", """{"phone":"10086"}"""),
            TestSpec("open_browser", """{"url":"https://example.com"}"""),
            TestSpec("share_text", """{"text":"Nyral Lite 工具自测 OK","title":"分享测试"}"""),
            TestSpec("start_foreground_task", """{"taskId":"self_test","interval_minutes":"1","notification_body":"1 分钟到啦！","run_once":"true"}"""),
            TestSpec("stop_foreground_task", """{"taskId":"self_test"}"""),
            TestSpec("get_location", "{}"),
            TestSpec("read_clipboard", "{}"),
            TestSpec("write_clipboard", """{"text":"Nyral Lite 剪贴板自测 OK"}"""),
            TestSpec("js_sandbox", "{}"),
        )
    }
}
