package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.WorkspaceStore

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

    /** 启动恢复：优先回到上次所在会话（包括只有草稿的新会话）。 */
    fun restore(onLoad: (List<Message>) -> Unit) {
        val current = workspace.currentSessionId
        if (current != null) {
            val session = store.load(current)
            if (session != null) {
                open(current, onLoad)
            } else {
                runtime.currentId = current
                runtime.personaId = workspace.currentPersonaId
                runtime.summaryText = ""
                runtime.summarizedUpTo = 0
                runtime.persisted = false
                onLoad(emptyList())
            }
            return
        }
        val latest = store.list().firstOrNull()
        if (latest == null) newSession(onLoad) else open(latest.id, onLoad)
    }

    /** 打开指定会话；文件损坏或不存在返回 false，界面保持原样。 */
    fun open(id: String, onLoad: (List<Message>) -> Unit): Boolean {
        val session = store.load(id) ?: return false
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
        return true
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
