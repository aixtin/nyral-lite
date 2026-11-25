package io.github.aixtin.nyrallite.plugins.storageprefs

import io.github.aixtin.nyrallite.contract.ChatSession
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.SessionSummary
import java.io.File

/**
 * [SessionStore] 的文件实现：每个会话一个 `<id>.json`，另用 `index.json` 存摘要。
 * 历史列表只读索引，不必解析全部会话正文。
 */
class FileSessionStore(private val dir: File) : SessionStore {

    private val indexFile = File(dir, SessionKeys.INDEX_FILE)
    private val lock = Any()

    override fun list(): List<SessionSummary> {
        synchronized(lock) {
            // 文件被外部删掉时顺带过滤，避免列表点进去空空如也
            return SessionIndex.read(indexFile)
                .filter { File(dir, "${it.id}.json").isFile }
                .sortedByDescending { it.updatedAt }
        }
    }

    override fun load(id: String): ChatSession? {
        synchronized(lock) {
            val file = File(dir, "$id.json")
            if (!file.isFile) return null
            return SessionJson.readSession(file, id)
        }
    }

    override fun save(session: ChatSession) {
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                SessionJson.writeAtomically(
                    File(dir, "${session.id}.json"),
                    SessionJson.writeSession(session),
                )
                SessionIndex.write(
                    indexFile,
                    SessionIndex.read(indexFile).filterNot { it.id == session.id } +
                        SessionSummary(session.id, session.title, session.updatedAt, session.personaId),
                )
            }
        }
    }

    override fun delete(id: String) {
        synchronized(lock) {
            runCatching {
                File(dir, "$id.json").delete()
                SessionIndex.write(indexFile, SessionIndex.read(indexFile).filterNot { it.id == id })
            }
        }
    }
}
