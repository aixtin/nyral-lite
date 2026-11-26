package io.github.aixtin.nyrallite.plugins.modelopenai

import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.ChatEvent
import io.github.aixtin.nyrallite.contract.ChatRequest
import io.github.aixtin.nyrallite.contract.ChatTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 标准 OpenAI 兼容端点传输：单一 SSE 解析路径，不按厂商分流。
 *
 * 刻意不做（对照母体）：不写 provider 分支、不做工具壳拦截、不做内容净化、
 * 不做多闸看门狗。协议问题只在协议层解决。
 */
class OpenAiTransport(
    private val configStore: ApiConfigStore,
    private val scope: CoroutineScope,
    /** 按引用读附件字节；由宿主注册的 AttachmentStores 提供。 */
    private val openAttachment: (AttachmentRef) -> ByteArray? = { null },
) : ChatTransport {

    override fun stream(request: ChatRequest, onEvent: (ChatEvent) -> Unit): Job = scope.launch {
        var conn: HttpURLConnection? = null
        try {
            val config = configStore.load()
            conn = (URL(config.baseUrl + "/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                // 推理模型(尤其 NVIDIA 托管)可能长时间不吐第一个 token, 60s 会误判超时
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                doOutput = true
            }
            val body = OpenAiRequestBody.build(config, request, openAttachment)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
                throw RuntimeException("HTTP $code: ${OpenAiErrorMessages.extract(err)}")
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val toolCalls = OpenAiToolCallAccumulator()
            while (true) {
                val raw = reader.readLine() ?: break
                if (SseLineReader.isDone(raw)) break
                val data = SseLineReader.payload(raw) ?: continue
                // 同一帧可能只有工具调用分片、没有正文增量，两路都要消费
                toolCalls.accept(data)
                val delta = OpenAiDeltaParser.parse(data) ?: continue
                withContext(Dispatchers.Main) { onEvent(delta) }
            }
            val calls = toolCalls.take()
            if (calls.isNotEmpty()) {
                withContext(Dispatchers.Main) { onEvent(ChatEvent.ToolCalls(calls)) }
            }
            withContext(Dispatchers.Main) { onEvent(ChatEvent.Done) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onEvent(ChatEvent.Error(e.message ?: "未知错误")) }
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private companion object {
        /** 建连超时（毫秒）。 */
        const val CONNECT_TIMEOUT_MS = 20_000

        /** 首包/包间读超时（毫秒）。推理模型首个 token 可能很久，60s 会误杀。 */
        const val READ_TIMEOUT_MS = 180_000
    }
}
