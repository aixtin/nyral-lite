package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatSession
import io.github.aixtin.nyrallite.contract.SessionMessage
import io.github.aixtin.nyrallite.contract.SessionToolCall
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 会话正文的 JSON 编解码；FileSessionStore 只负责加锁与目录组织。 */
internal object SessionJson {

    fun readSession(file: File, id: String): ChatSession? = runCatching {
        val json = JSONObject(file.readText())
        val array = json.optJSONArray(SessionKeys.MESSAGES) ?: JSONArray()
        val messages = ArrayList<SessionMessage>(array.length())
        for (i in 0 until array.length()) {
            messages += readMessage(array.getJSONObject(i))
        }
        ChatSession(
            id = json.optString(SessionKeys.ID, id),
            title = json.optString(SessionKeys.TITLE, ""),
            updatedAt = json.optLong(SessionKeys.UPDATED_AT, file.lastModified()),
            messages = messages,
            personaId = json.optString(SessionKeys.PERSONA_ID).ifBlank { null },
            summary = json.optString(SessionKeys.SUMMARY),
            summaryUpTo = json.optInt(SessionKeys.SUMMARY_UP_TO).coerceIn(0, messages.size),
        )
    }.getOrNull()

    fun writeSession(session: ChatSession): String {
        val array = JSONArray()
        session.messages.forEach { m ->
            val obj = JSONObject()
                .put(SessionKeys.ROLE, m.role)
                .put(SessionKeys.CONTENT, m.content)
                .put(SessionKeys.THINKING, m.thinking)
            m.tool?.let { t ->
                obj.put(
                    SessionKeys.TOOL,
                    JSONObject()
                        .put(SessionKeys.NAME, t.name)
                        .put(SessionKeys.ARGUMENTS, t.arguments)
                        .put(SessionKeys.RESULT, t.result)
                        .put(SessionKeys.OK, t.ok)
                        .put(SessionKeys.DURATION_MS, t.durationMs),
                )
            }
            if (m.attachments.isNotEmpty()) {
                obj.put(SessionKeys.ATTACHMENTS, writeAttachments(m.attachments))
            }
            array.put(obj)
        }
        return JSONObject()
            .put(SessionKeys.ID, session.id)
            .put(SessionKeys.TITLE, session.title)
            .put(SessionKeys.UPDATED_AT, session.updatedAt)
            .put(SessionKeys.PERSONA_ID, session.personaId)
            .put(SessionKeys.SUMMARY, session.summary)
            .put(SessionKeys.SUMMARY_UP_TO, session.summaryUpTo)
            .put(SessionKeys.MESSAGES, array)
            .toString()
    }

    /** 先写临时文件再改名，失败时退回直接覆盖。 */
    fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    private fun readMessage(m: JSONObject): SessionMessage {
        val tool = m.optJSONObject(SessionKeys.TOOL)?.let {
            SessionToolCall(
                name = it.optString(SessionKeys.NAME),
                arguments = it.optString(SessionKeys.ARGUMENTS),
                result = it.optString(SessionKeys.RESULT),
                ok = it.optBoolean(SessionKeys.OK, true),
                durationMs = it.optLong(SessionKeys.DURATION_MS),
            )
        }
        return SessionMessage(
            role = m.optString(SessionKeys.ROLE),
            content = m.optString(SessionKeys.CONTENT),
            thinking = m.optString(SessionKeys.THINKING),
            tool = tool,
            attachments = readAttachments(m.optJSONArray(SessionKeys.ATTACHMENTS)),
        )
    }

    private fun writeAttachments(refs: List<AttachmentRef>): JSONArray = JSONArray().apply {
        refs.forEach { ref ->
            put(
                JSONObject()
                    .put(SessionKeys.STORE, ref.store)
                    .put(SessionKeys.KEY, ref.key)
                    .put(SessionKeys.MIME, ref.mime)
                    .put(SessionKeys.NAME, ref.name)
                    .put(SessionKeys.SIZE, ref.size),
            )
        }
    }

    /** 附件引用损坏时逐条跳过，不影响整条会话的读取。 */
    private fun readAttachments(array: JSONArray?): List<AttachmentRef> {
        if (array == null) return emptyList()
        val result = ArrayList<AttachmentRef>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val key = o.optString(SessionKeys.KEY)
            if (key.isEmpty()) continue
            result += AttachmentRef(
                store = o.optString(SessionKeys.STORE),
                key = key,
                mime = o.optString(SessionKeys.MIME),
                name = o.optString(SessionKeys.NAME),
                size = o.optLong(SessionKeys.SIZE),
            )
        }
        return result
    }
}
