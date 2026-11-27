package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import io.github.aixtin.nyrallite.contract.ChatSession
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.WorkspaceStore
import java.util.concurrent.Executors

/** 会话的恢复、打开、新建与删除；状态落在 [SessionRuntime]。 */
internal class SessionOpener(
    private val store: SessionStore,
    private val workspace: WorkspaceStore,
    private val personas: PersonaStore,
    private val messages: List<Message>,
    private val drafts: DraftController,
    private val runtime: SessionRuntime,
    /** 切走当前会话前的通知：用于整理长期记忆，失败不影响切换。 */
    private val onSessionEnding: (String, List<Message>) -> Unit,
    /** 切换时取消尚未执行的防抖保存，避免把旧会话写到新 id。 */
    private val cancelPendingSave: () -> Unit,
) {

    private val io = Executors.newSingleThreadExecutor()

    private fun main(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    /** 启动恢复：后台读文件，读到前界面显示骨架占位；优先回到上次所在会话（包括只有草稿的新会话）。 */
    fun restore(onLoad: (List<Message>) -> Unit) {
        val current = workspace.currentSessionId
        io.execute {
            val session = if (current != null) store.load(current)
            else store.list().firstOrNull()?.let { store.load(it.id) }
            main {
                when {
                    current != null && session != null -> applySession(session, onLoad)
                    current != null -> {
                        runtime.currentId = current
                        runtime.personaId = workspace.currentPersonaId
                        runtime.summaryText = ""
                        runtime.summarizedUpTo = 0
                        runtime.persisted = false
                        onLoad(emptyList())
                    }
                    session != null -> applySession(session, onLoad)
                    else -> newSession(onLoad)
                }
            }
        }
    }

    /** 打开指定会话；文件损坏或不存在返回 false，界面保持原样。 */
    fun open(id: String, onLoad: (List<Message>) -> Unit): Boolean {
        val session = store.load(id) ?: return false
        applySession(session, onLoad)
        return true
    }

    private fun applySession(session: ChatSession, onLoad: (List<Message>) -> Unit) {
        notifyEnding()
        drafts.flush()
        cancelPendingSave()
        runtime.currentId = session.id
        runtime.personaId = session.personaId
        runtime.summaryText = session.summary
        runtime.summarizedUpTo = session.summaryUpTo.coerceIn(0, session.messages.size)
        runtime.persisted = true
        workspace.currentSessionId = session.id
        workspace.currentPersonaId = session.personaId
        onLoad(session.messages.mapNotNull(SessionMessages::toMessage))
    }

    /** 新建空会话：只记录当前 id，不进入历史列表，等第一条消息产生后再存正文。 */
    fun newSession(onLoad: (List<Message>) -> Unit) {
        notifyEnding()
        drafts.flush()
        cancelPendingSave()
        runtime.currentId = System.currentTimeMillis().toString()
        runtime.personaId = personas.defaultId
        runtime.summaryText = ""
        runtime.summarizedUpTo = 0
        runtime.persisted = false
        workspace.currentSessionId = runtime.currentId
        workspace.currentPersonaId = runtime.personaId
        onLoad(emptyList())
    }

    /** 删除会话；若删的是当前会话，界面切到空会话。 */
    fun delete(id: String, onLoad: (List<Message>) -> Unit) {
        if (id == runtime.currentId) drafts.flush()
        store.delete(id)
        drafts.delete(id)
        if (id == runtime.currentId) newSession(onLoad)
    }

    /** 切走前把当前会话交给外部（整理记忆）；没有消息或没有 id 时跳过。 */
    private fun notifyEnding() {
        val id = runtime.currentId ?: return
        if (messages.isEmpty()) return
        runCatching { onSessionEnding(id, messages.toList()) }
    }
}
