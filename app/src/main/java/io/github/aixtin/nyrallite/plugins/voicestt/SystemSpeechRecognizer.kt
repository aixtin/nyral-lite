package io.github.aixtin.nyrallite.plugins.voicestt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.contract.SpeechRecognizer as ContractSpeechRecognizer

/**
 * 系统语音识别默认实现：直接委托 Android 自带 [SpeechRecognizer]。
 *
 * 国内 ROM（ColorOS / MIUI 等）普遍阉割 Google 语音识别服务，
 * 此时 [isAvailable] 会返回 false；用户可自行安装 GMS 或替换成离线/云端插件。
 */
class SystemSpeechRecognizer(
    private val context: Context,
    private val listener: ContractSpeechRecognizer.Listener,
) : ContractSpeechRecognizer {

    private var recognizer: SpeechRecognizer? = null
    private var state = ContractSpeechRecognizer.State.IDLE

    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun currentState(): ContractSpeechRecognizer.State = state

    override fun start() {
        if (state != ContractSpeechRecognizer.State.IDLE) return
        if (!isAvailable()) {
            listener.onError(ContractSpeechRecognizer.Errors.UNAVAILABLE)
            return
        }
        if (!hasPermission()) {
            listener.onError(ContractSpeechRecognizer.Errors.PERMISSION)
            return
        }
        try {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(RecognitionBridge())
            r.startListening(buildIntent())
            state = ContractSpeechRecognizer.State.LISTENING
            listener.onStateChange(state)
        } catch (e: Exception) {
            recognizer?.destroy()
            recognizer = null
            state = ContractSpeechRecognizer.State.IDLE
            listener.onError(ContractSpeechRecognizer.Errors.UNAVAILABLE)
        }
    }

    override fun stop() {
        if (state == ContractSpeechRecognizer.State.LISTENING) {
            state = ContractSpeechRecognizer.State.PROCESSING
            listener.onStateChange(state)
            recognizer?.stopListening()
        }
    }

    override fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        state = ContractSpeechRecognizer.State.IDLE
        listener.onStateChange(state)
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LocaleCompat.tag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private object LocaleCompat {
        fun tag(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            java.util.Locale.getDefault().toLanguageTag()
        } else {
            val l = java.util.Locale.getDefault()
            "${l.language}_${l.country}"
        }
    }

    private inner class RecognitionBridge : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            state = ContractSpeechRecognizer.State.LISTENING
            listener.onStateChange(state)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { listener.onVolume(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            state = ContractSpeechRecognizer.State.PROCESSING
            listener.onStateChange(state)
        }
        override fun onError(error: Int) {
            val mapped = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> ContractSpeechRecognizer.Errors.AUDIO
                SpeechRecognizer.ERROR_CLIENT -> ContractSpeechRecognizer.Errors.UNAVAILABLE
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ContractSpeechRecognizer.Errors.PERMISSION
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ContractSpeechRecognizer.Errors.NETWORK
                SpeechRecognizer.ERROR_NO_MATCH -> ContractSpeechRecognizer.Errors.NO_MATCH
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ContractSpeechRecognizer.Errors.BUSY
                SpeechRecognizer.ERROR_SERVER -> ContractSpeechRecognizer.Errors.UNAVAILABLE
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ContractSpeechRecognizer.Errors.TIMEOUT
                else -> ContractSpeechRecognizer.Errors.UNKNOWN
            }
            state = ContractSpeechRecognizer.State.IDLE
            listener.onStateChange(state)
            listener.onError(mapped)
        }
        override fun onResults(results: Bundle?) {
            state = ContractSpeechRecognizer.State.IDLE
            listener.onStateChange(state)
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull().orEmpty()
            if (text.isBlank()) listener.onError(ContractSpeechRecognizer.Errors.NO_MATCH)
            else listener.onResult(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            listener.onPartial(list?.firstOrNull().orEmpty())
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
