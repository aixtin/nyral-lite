package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.voicestt.SpeechRecognizerFactory

/**
 * 只做界面装配：布局、按钮入口与生命周期转发；
 * 会话与聊天流的装配在 [ChatWiring]，附件在 [ImageAttachments]，语音在 [VoiceInput]。
 */
class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<Message>()
    private lateinit var wiring: ChatWiring
    private lateinit var scrollButton: ScrollToBottomButton
    private lateinit var header: PersonaHeader
    private lateinit var mcpWarning: McpWarningBar
    private lateinit var input: EditText
    private lateinit var voice: VoiceInput

    private lateinit var attachmentStores: AttachmentStores
    /** 待发送附件：选图即转码落盘，发送时整批交给 ChatController。 */
    private lateinit var attachments: ImageAttachments
    private lateinit var composerAttachments: ComposerAttachments

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 工具确认对话框需要前台界面：登记当前 Activity
        (application as App).approval.attach(this)

        // 消费状态栏 / 导航栏 / 键盘 inset: 标题栏不被遮挡, 输入栏与贴底消息跟随键盘抬起
        val root = findViewById<View>(R.id.root)
        val rv = findViewById<RecyclerView>(R.id.list)
        KeyboardInsets(root, rv).install()
        input = findViewById(R.id.input)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val attachStrip = findViewById<View>(R.id.attach_scroll)
        // 空输入且无待发附件时置灰发送键；输出中按钮是“停止”，保持可点
        fun refreshSendState() {
            val idle = btnSend.text.toString() == getString(R.string.send)
            val canSend = input.text?.toString()?.isNotBlank() == true ||
                attachStrip.visibility == View.VISIBLE
            btnSend.isEnabled = !idle || canSend
            btnSend.alpha = if (btnSend.isEnabled) 1f else 0.4f
        }
        header = PersonaHeader.attach(this)
        mcpWarning = McpWarningBar.attach(this)

        attachmentStores = kernel.services.get<AttachmentStores>()
        attachments = ImageAttachments(this, attachmentStores) { composerAttachments.render(it) }
        wiring = ChatWiring(this, rv, input, btnSend, messages, attachments::take) {
            header.refresh(wiring.sessions.currentPersonaId)
        }
        wiring.start()
        wiring.controller.onToolsWarning = mcpWarning::show

        btnSend.setOnClickListener {
            wiring.controller.onSendButtonClick()
            // 发送成功会清空输入框，立即清掉对应草稿，不等防抖
            wiring.flushDraft()
        }
        // 点击标题栏进入设置页；有人设时这一栏显示头像 + 名字
        findViewById<View>(R.id.top_bar).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // 门禁档位改在 设置 → 安全门禁 里调整，底排只留看得懂的入口
        findViewById<TextView>(R.id.toolbar_plugins).setOnClickListener {
            startActivity(Intent(this, PluginsActivity::class.java))
        }
        findViewById<TextView>(R.id.toolbar_history).setOnClickListener { wiring.openHistory() }
        findViewById<TextView>(R.id.toolbar_memory).setOnClickListener {
            startActivity(Intent(this, MemoryActivity::class.java))
        }
        findViewById<TextView>(R.id.toolbar_persona).setOnClickListener {
            wiring.persona.launch(wiring.sessions.currentPersonaId)
        }
        voice = VoiceInput(
            this,
            input,
            findViewById(R.id.bar_voice),
            kernel.services.getOrNull<SpeechRecognizerFactory>(),
        )

        composerAttachments = ComposerAttachments(
            scroll = findViewById(R.id.attach_scroll),
            strip = findViewById(R.id.attach_strip),
            open = { ref -> attachmentStores.open(ref) },
            onRemove = attachments::removeAt,
            onChanged = { refreshSendState() },
        )
        input.doAfterTextChanged { refreshSendState() }
        refreshSendState()
        findViewById<TextView>(R.id.bar_attach).setOnClickListener { attachments.pick() }

        scrollButton = ScrollToBottomButton(
            rv = rv,
            button = findViewById<ImageView>(R.id.btn_scroll_bottom),
            onScrollStateChanged = { state -> wiring.controller.listScrollState = state },
            onPinToBottom = wiring.controller::pinToBottom,
        )
    }

    override fun onResume() {
        super.onResume()
        // 历史页可能删掉了当前会话：回到主界面时切到空会话
        wiring.resetIfMissing()
        header.refresh(wiring.sessions.currentPersonaId)
        if (!kernel.services.get<ApiConfigStore>().isConfigured()) {
            startActivity(Intent(this, ModelConfigActivity::class.java))
        }
        // 启动时静默检查更新（6 小时节流，有新版本才弹一次框）
        UpdateChecker.checkIfDue(this)
    }

    override fun onPause() {
        // 切后台时立即保存，避免系统在防抖窗口内回收进程丢草稿
        wiring.flushDraft()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        attachments.dispose()
        voice.dispose()
        (application as App).approval.detach(this)
        wiring.dispose()
        scrollButton.dispose()
    }
}
