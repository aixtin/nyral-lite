package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.Message
import io.github.aixtin.nyrallite.contract.ChatEvent
import io.github.aixtin.nyrallite.contract.ChatRequest
import io.github.aixtin.nyrallite.contract.ChatTransport
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.MemoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 会话结束时整理长期记忆：从刚才那轮对话里提炼 0~3 条值得跨会话保留的事实。
 *
 * 刻意做成「异步、尽力而为」：失败静默，绝不阻塞新会话；同一会话只整理一次
 * （标记落 SharedPreferences），避免来回切换反复烧 token。
 */
class MemoryExtractor(
    private val transport: ChatTransport,
    private val store: MemoryStore,
    private val marker: SessionMarker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun maybeExtract(sessionId: String, messages: List<Message>) {
        if (sessionId.isBlank() || messages.size < MIN_MESSAGES) return
        if (marker.isExtracted(sessionId)) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                val known = runCatching { store.all().map { it.text } }.getOrDefault(emptyList())
                MemoryDedup.filter(known, extract(messages)).forEach { (title, text) ->
                    store.add(text = text, title = title, sessionId = sessionId)
                }
                // 只有成功跑完才标记；网络/配置失败留给下次切换重试
                marker.markExtracted(sessionId)
            } catch (_: Exception) {
                // 失败静默：不打扰用户，也不标记
            } finally {
                running.set(false)
            }
        }
    }

    fun dispose() = scope.cancel()

    private suspend fun extract(messages: List<Message>): List<Pair<String, String>> {
        val transcript = messages
            .filter { (it.role == Message.ROLE_USER || it.role == Message.ROLE_AI) && it.content.isNotBlank() }
            .takeLast(MAX_MESSAGES)
            .joinToString("\n") { "${it.role}: ${it.content.take(MAX_CHARS_PER_MESSAGE)}" }
        if (transcript.isBlank()) return emptyList()

        val request = ChatRequest(
            systemPrompt = promptWithKnown(),
            turns = listOf(ChatTurn(ChatTurn.ROLE_USER, transcript)),
        )
        val raw = StringBuilder()
        suspendCancellableCoroutine { cont ->
            val job = transport.stream(request) { event ->
                when (event) {
                    is ChatEvent.Delta -> raw.append(event.content)
                    is ChatEvent.ToolCalls -> Unit
                    ChatEvent.Done, is ChatEvent.Error -> if (cont.isActive) cont.resume(Unit)
                }
            }
            cont.invokeOnCancellation { job.cancel() }
        }
        return parse(raw.toString())
    }

    /** 把已有记忆的正文附在提示词里，让模型别把同一件事重复记一遍；只给标题不够。 */
    private fun promptWithKnown(): String {
        val known = runCatching {
            store.all().takeLast(MAX_KNOWN).map { "- ${it.text.take(MAX_KNOWN_CHARS)}" }
        }.getOrDefault(emptyList()).filter { it.length > 2 }
        if (known.isEmpty()) return PROMPT
        return PROMPT + "\n已有记忆（同一事实不要重复提取）：\n" + known.joinToString("\n")
    }

    /** 解析「标题|内容」，容忍模型加序号、引号或说 NONE。 */
    private fun parse(raw: String): List<Pair<String, String>> {
        if (raw.contains("NONE", ignoreCase = true) && !raw.contains('|')) return emptyList()
        return raw.lines()
            .asSequence()
            .map { it.trim().trim('-', '*', '1', '2', '3', '.', ' ', '\t') }
            .filter { it.contains('|') }
            .map { line ->
                val parts = line.split('|', limit = 2)
                cleanTitle(parts[0]) to parts[1].trim().trim('"', '“', '”')
            }
            .filter { it.second.length >= MIN_TEXT_CHARS }
            .take(MAX_ENTRIES)
            .toList()
    }

    private fun cleanTitle(raw: String): String = raw.trim()
        .trim('「', '」', '"', '“', '”', '[', ']', '#')
        .take(MAX_TITLE_CHARS)

    private companion object {
        const val MIN_MESSAGES = 6
        const val MAX_MESSAGES = 30
        const val MAX_CHARS_PER_MESSAGE = 300
        const val MAX_ENTRIES = 3
        const val MIN_TEXT_CHARS = 4
        const val MAX_TITLE_CHARS = 14
        const val MAX_KNOWN = 20
        const val MAX_KNOWN_CHARS = 60

        const val PROMPT = "从下面的对话中提取值得长期记住的用户信息：偏好、长期设定、身份/项目信息、明确要求的规则。" +
            "不要提取一次性任务、临时数据、闲聊情绪。最多 3 条，每条一行，格式：标题|内容。" +
            "标题不超过 12 字；内容是一句自包含的陈述，不要用代词。没有值得记住的内容就只输出 NONE。不要输出其它说明。"
    }
}

/** 已整理标记，避免同一会话被反复提炼。 */
class SessionMarker(private val prefs: android.content.SharedPreferences) {
    fun isExtracted(sessionId: String): Boolean = prefs.getString(KEY, null) == sessionId
    fun markExtracted(sessionId: String) {
        prefs.edit().putString(KEY, sessionId).apply()
    }

    private companion object {
        const val KEY = "last_session"
    }
}
