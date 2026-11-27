package io.github.aixtin.nyrallite

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 工具自测页：一键触发系统原生工具，验证链路是否通。 */
class ToolTestActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_test)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        ToolTestPanel(this, scope, findViewById(R.id.tool_test_panel))
    }

    override fun onResume() {
        super.onResume()
        // 门禁确认弹窗挂在登记的前台 Activity 上；不登记会弹到栈底窗口，被本页挡住
        (application as App).approval.attach(this)
    }

    override fun onPause() {
        // 与 onResume 的登记对称：返回时 MainActivity 会在自己的 onResume 重新登记
        (application as App).approval.detach(this)
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
