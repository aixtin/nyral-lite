package io.github.aixtin.nyrallite

import android.os.Handler
import android.os.Looper
import io.github.aixtin.nyrallite.contract.WorkspaceStore

/** 当前会话的输入草稿：输入时防抖保存，切会话或退后台前立即落盘。 */
class DraftController(
    private val store: WorkspaceStore,
    private val currentSessionId: () -> String?,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingText: String? = null
    private val saveTask = Runnable { flush() }

    fun load(): String {
        val id = currentSessionId() ?: return ""
        return store.loadDraft(id)
    }

    fun schedule(text: String) {
        pendingText = text
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, SAVE_DELAY_MS)
    }

    fun flush() {
        handler.removeCallbacks(saveTask)
        val text = pendingText ?: return
        val id = currentSessionId() ?: return
        store.saveDraft(id, text)
        pendingText = null
    }

    fun delete(sessionId: String) {
        store.deleteDraft(sessionId)
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
