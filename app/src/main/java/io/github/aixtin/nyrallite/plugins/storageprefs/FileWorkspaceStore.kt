package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.WorkspaceStore
import org.json.JSONObject
import java.io.File

/** 工作区状态的文件实现；所有读写串行化，草稿按会话 id 存放。 */
class FileWorkspaceStore(dir: File) : WorkspaceStore {

    private val file = File(dir, WORKSPACE_FILE)
    private val lock = Any()

    override var currentSessionId: String?
        get() = synchronized(lock) {
            read().optString(KEY_CURRENT_ID).ifBlank { null }
        }
        set(value) = synchronized(lock) {
            val json = read()
            if (value == null) json.remove(KEY_CURRENT_ID) else json.put(KEY_CURRENT_ID, value)
            write(json)
        }

    override var currentPersonaId: String?
        get() = synchronized(lock) {
            read().optString(KEY_PERSONA_ID).ifBlank { null }
        }
        set(value) = synchronized(lock) {
            val json = read()
            if (value == null) json.remove(KEY_PERSONA_ID) else json.put(KEY_PERSONA_ID, value)
            write(json)
        }

    override fun loadDraft(sessionId: String): String = synchronized(lock) {
        read().optJSONObject(KEY_DRAFTS)?.optString(sessionId).orEmpty()
    }

    override fun saveDraft(sessionId: String, text: String) {
        synchronized(lock) {
            val json = read()
            val drafts = json.optJSONObject(KEY_DRAFTS) ?: JSONObject().also { json.put(KEY_DRAFTS, it) }
            if (text.isBlank()) drafts.remove(sessionId) else drafts.put(sessionId, text)
            write(json)
        }
    }

    override fun deleteDraft(sessionId: String) {
        synchronized(lock) {
            val json = read()
            json.optJSONObject(KEY_DRAFTS)?.remove(sessionId)
            write(json)
        }
    }

    private fun read(): JSONObject = runCatching {
        if (file.isFile) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun write(json: JSONObject) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val WORKSPACE_FILE = "workspace.json"
        const val KEY_CURRENT_ID = "currentId"
        const val KEY_PERSONA_ID = "currentPersonaId"
        const val KEY_DRAFTS = "drafts"
    }
}
