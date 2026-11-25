package io.github.aixtin.nyrallite.contract

import kotlinx.coroutines.Job

/**
 * 流式聊天传输接口。实现方（model-openai 等插件）负责厂商协议，
 * 主体只消费 [ChatEvent]。
 */
interface ChatTransport {
    /**
     * 发起一次流式请求。事件回调保证在主线程触发；
     * 返回的 Job 可取消，取消后不再回调。
     */
    fun stream(request: ChatRequest, onEvent: (ChatEvent) -> Unit): Job
}
