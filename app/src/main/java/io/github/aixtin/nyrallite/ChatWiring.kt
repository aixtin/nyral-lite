package io.github.aixtin.nyrallite

import android.content.Context
import android.widget.Button
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.contract.ContentRenderer
import io.github.aixtin.nyrallite.contract.MemoryStore
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.ToolRegistry
import io.github.aixtin.nyrallite.contract.WorkspaceStore
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.memorystore.MemoryExtractor
import io.github.aixtin.nyrallite.plugins.memorystore.SessionMarker

/**
 * 会话与聊天流的装配：会话控制、聊天流、摘要、记忆提炼、人设与历史切换。
 * 主界面只把 UI 对象交进来，业务对象的创建与释放都在这里。
 */
internal class ChatWiring(
    private val activity: AppCompatActivity,
    private val rv: RecyclerView,
    private val input: EditText,
    private val btnSend: Button,
    private val messages: MutableList<Message>,
    private val takeAttachments: () -> List<AttachmentRef>,
    private val onPersonaChanged: () -> Unit = {},
) {
    lateinit var sessions: SessionController
        private set
    lateinit var controller: ChatController
        private set
    lateinit var persona: PersonaSwitcher
        private set

    private var memoryExtractor: MemoryExtractor? = null
    private var summarizer: SessionSummarizer? = null

    /** 空会话状态：人设头像 + 欢迎语，消息列表驱动可见性。 */
    lateinit var emptyState: EmptyState
        private set

    private val hub = activity.kernel.services
    private lateinit var history: HistorySwitcher

    fun start() {
        // 会话切走时异步提炼长期记忆；没配置模型时静默跳过
        memoryExtractor = hub.getOrNull<MemoryStore>()?.let { memoryStore ->
            MemoryExtractor(
                transport = hub.get<ChatTransport>(),
                store = memoryStore,
                marker = SessionMarker(
                    activity.getSharedPreferences("memory_extract", Context.MODE_PRIVATE),
                ),
            )
        }
        summarizer = SessionSummarizer(hub.get<ChatTransport>()) { id, text, upTo ->
            sessions.applySummary(id, text, upTo)
        }
        val adapter = ChatAdapter(
            messages,
            hub.get<ContentRenderer>(),
            onRetry = { position -> controller.retry(position) },
            openAttachment = { ref -> hub.get<AttachmentStores>().open(ref) },
            onAttachmentClick = { ref ->
                activity.startActivity(AttachmentViewerActivity.intent(activity, ref))
            },
        )
        // 不用 stackFromEnd: 短会话贴底会导致展开思考时整块内容只能上移(向上展开)。
        rv.layoutManager = LinearLayoutManager(activity)
        rv.adapter = adapter
        emptyState = EmptyState.attach(activity, adapter) { messages.isNotEmpty() }

        sessions = SessionController(
            hub.get<SessionStore>(),
            hub.get<WorkspaceStore>(),
            hub.get<PersonaStore>(),
            messages,
            onSessionEnding = { id, msgs -> memoryExtractor?.maybeExtract(id, msgs) },
        )
        emptyState.refresh(sessions.currentPersonaId)
        controller = ChatController(
            activity = activity,
            rv = rv,
            adapter = adapter,
            messages = messages,
            input = input,
            btnSend = btnSend,
            transport = hub.get<ChatTransport>(),
            toolRegistry = hub.getOrNull<ToolRegistry>(),
            currentPersonaId = { sessions.currentPersonaId },
            sessionSummary = { sessions.summaryPrompt },
            contextBudget = {
                val tokens = hub.getOrNull<ApiConfigStore>()?.load()?.contextTokens ?: 0
                if (tokens > 0) tokens else ContextWindow.DEFAULT_MAX_TOKENS
            },
            onRoundEnd = { force ->
                summarizer?.maybeSummarize(
                    sessions.currentSessionId,
                    sessions.currentSummary,
                    sessions.summarizedCount,
                    messages,
                    force = force,
                )
            },
            takePendingAttachments = takeAttachments,
            onChanged = { sessions.scheduleSave() },
        )
        // 启动恢复上次所在会话与未发送草稿；没有历史时创建空会话
        // 会话恢复：后台读取，读到前显示骨架占位
        val skeleton = activity.findViewById<View>(R.id.chat_skeleton)
        skeleton.visibility = View.VISIBLE
        sessions.restore { loaded ->
            skeleton.visibility = View.GONE
            controller.replaceSession(loaded)
        }
        loadDraft()
        input.doAfterTextChanged { sessions.scheduleDraftSave(it?.toString().orEmpty()) }
        history = HistorySwitcher(activity, sessions, controller::replaceSession, ::loadDraft)
        persona = PersonaSwitcher(
            activity, sessions, controller::replaceSession, ::loadDraft,
            hasMessages = { messages.isNotEmpty() },
            greetingOf = { id -> PersonaGreeting.of(activity.application as App, id) },
            insertGreeting = controller::insertGreeting,
            onApplied = onPersonaChanged,
        )
    }

    fun openHistory() = history.launch()

    /** 历史页可能删掉了当前会话：回到主界面时切到空会话。 */
    fun resetIfMissing() {
        if (sessions.currentAvailable()) return
        sessions.newSession(controller::replaceSession)
        loadDraft()
    }

    fun flushDraft() = sessions.flushDraft()

    fun dispose() {
        memoryExtractor?.dispose()
        summarizer?.dispose()
        sessions.dispose()
        controller.onDestroy()
    }

    private fun loadDraft() {
        val draft = sessions.loadDraft()
        input.setText(draft)
        input.setSelection(draft.length)
    }
}
