package io.github.aixtin.nyrallite.contract

/**
 * 语音识别契约：主包不依赖任何具体实现，所有 STT 后端（系统服务、离线模型、云端 API）
 * 都通过插件向 kernel 注册这同一个接口。
 *
 * 典型用法：点击语音按钮 → [isAvailable] + [hasPermission] 前置检查
 *           → [start] 开始录音 → [stop] 结束 → [Listener.onResult] 回调文字
 * 中间状态（录音中/识别中/音量/部分结果）通过 [Listener] 通知 UI 切换。
 */
interface SpeechRecognizer {

    /** 插件是否准备好：系统 STT 可能被 ROM 阉割，离线模型可能还没下载。 */
    fun isAvailable(): Boolean

    /** 是否已具备录音权限；未授权时主体弹系统请求。 */
    fun hasPermission(): Boolean

    /** 当前状态。 */
    fun currentState(): State

    /** 开始一轮识别；调用方应确保权限已授予且 [isAvailable] 为 true。 */
    fun start()

    /** 结束录音，等待最终结果回调（或超时/错误）。 */
    fun stop()

    /** 取消当前识别，立即回到 IDLE。 */
    fun cancel()

    /** 进程结束/UI 销毁时释放底层资源（AudioRecord / SpeechRecognizer / 网络连接）。 */
    fun release()

    enum class State { IDLE, LISTENING, PROCESSING }

    interface Listener {
        fun onStateChange(state: State)
        fun onResult(text: String)
        fun onPartial(text: String)
        fun onVolume(rmsdB: Float)
        fun onError(code: Int)
    }

    companion object Errors {
        const val UNKNOWN = -1
        const val PERMISSION = 1
        const val UNAVAILABLE = 2
        const val NETWORK = 3
        const val NO_MATCH = 4
        const val TIMEOUT = 5
        const val BUSY = 6
        const val AUDIO = 7
    }
}
