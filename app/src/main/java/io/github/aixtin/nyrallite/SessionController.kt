package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import io.github.aixtin.nyrallite.contract.ChatSession
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.WorkspaceStore

/**
 * 会话运行时：对外 API 与防抖落盘。
 * 打开/新建/删除交给 [SessionOpener]，消息与持久化格式的转换交给 [SessionMessages]。
 * 消息列表由界面持有，保存做防抖，流式期间的高频变更不会反复写文件。
 */
class SessionController(
    private val store: SessionStore,
    private val workspace: WorkspaceStore,
    personas: PersonaStore,
    private val messages: MutableList<Message>,
    onSessionEnding: (String, List<Message>) -> Unit = { _, _ -> },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { saveNow() }
    private val runtime = SessionRuntime()
    private val drafts = DraftController(workspace) { runtime.currentId }
    private val opener = SessionOpener(
        store, workspace, personas, messages, drafts, runtime, onSessionEnding,
    ) { handler.removeCallbacks(saveTask) }

    val currentSessionId: String? get() = runtime.currentId
    val currentPersonaId: String? get() = runtime.personaId
    val currentSummary: String get() = runtime.summaryText
    val summarizedCount: Int get() = runtime.summarizedUpTo

    /** 拼进 system prompt 的前情摘要块；没有摘要时为空串。 */
    val summaryPrompt: String get() = SummaryText.block(runtime.summaryText, runtime.summarizedUpTo)

    fun restore(onLoad: (List<Message>) -> Unit) = opener.restore(onLoad)

    fun open(id: String, onLoad: (List<Message>) -> Unit): Boolean = opener.open(id, onLoad)

    fun newSession(onLoad: (List<Message>) -> Unit) = opener.newSession(onLoad)

    fun delete(id: String, onLoad: (List<Message>) -> Unit) = opener.delete(id, onLoad)

    /** 仅改当前会话绑定；已有消息会立即安排重写索引，不改任何聊天正文。 */
    fun setPersona(id: String?) {
        runtime.personaId = id
        workspace.currentPersonaId = id
        scheduleSave()
    }

    /** 后台摘要完成：会话已切走时丢弃，避免把摘要写进别人的会话。 */
    fun applySummary(sessionId: String, text: String, upTo: Int) {
        if (sessionId != runtime.currentId || text.isBlank()) return
        runtime.summaryText = text
        runtime.summarizedUpTo = upTo.coerceIn(0, messages.size)
        scheduleSave()
    }

    /** 消息有变更：防抖落盘。 */
    fun scheduleSave() {
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, SAVE_DELAY_MS)
    }

    fun loadDraft(): String = drafts.load()

    fun scheduleDraftSave(text: String) = drafts.schedule(text)

    fun flushDraft() = drafts.flush()

    /** 立即落盘消息与草稿（退出前调用）。 */
    fun flush() {
        handler.removeCallbacks(saveTask)
        saveNow()
        drafts.flush()
    }

    fun dispose() {
        flush()
        handler.removeCallbacks(saveTask)
    }

    /** 当前会话是否仍存在：尚未产生正文的新会话/纯草稿会话视为可用。 */
    fun currentAvailable(): Boolean {
        if (!runtime.persisted) return true
        val id = runtime.currentId ?: return true
        return store.list().any { it.id == id }
    }

    private fun saveNow() {
        val visible = SessionMessages.visible(messages)
        if (visible.isEmpty()) return
        val id = runtime.currentId ?: System.currentTimeMillis().toString().also {
            runtime.currentId = it
            workspace.currentSessionId = it
        }
        store.save(
            ChatSession(
                id = id,
                title = SessionMessages.titleOf(visible),
                updatedAt = System.currentTimeMillis(),
                messages = visible.map(SessionMessages::toSessionMessage),
                personaId = runtime.personaId,
                summary = runtime.summaryText,
                summaryUpTo = runtime.summarizedUpTo.coerceAtMost(visible.size),
            ),
        )
        runtime.persisted = true
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L
    }
}
