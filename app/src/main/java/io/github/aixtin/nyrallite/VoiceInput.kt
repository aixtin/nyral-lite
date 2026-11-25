package io.github.aixtin.nyrallite

import android.Manifest
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.aixtin.nyrallite.contract.SpeechRecognizer
import io.github.aixtin.nyrallite.plugins.voicestt.SpeechRecognizerFactory

/**
 * 语音输入：权限申请、录音状态与识别结果回填输入框。
 * 识别过程中不覆盖输入框，避免打断用户手动编辑。
 */
internal class VoiceInput(
    private val activity: ComponentActivity,
    private val input: EditText,
    private val button: TextView,
    factory: SpeechRecognizerFactory?,
) {
    private val listener = Listener()
    private val recognizer = factory?.create(listener)

    /** 麦克风权限请求：授予后直接启动录音。 */
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) recognizer?.start() else toast(R.string.voice_permission)
    }

    init {
        button.setOnClickListener { onClick() }
    }

    fun dispose() = recognizer?.release()

    private fun onClick() {
        val rec = recognizer
        if (rec == null) {
            toast(R.string.voice_unavailable)
            return
        }
        when (rec.currentState()) {
            SpeechRecognizer.State.IDLE -> {
                if (!rec.isAvailable()) {
                    toast(R.string.voice_unavailable)
                    return
                }
                if (!rec.hasPermission()) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return
                }
                rec.start()
            }
            SpeechRecognizer.State.LISTENING -> rec.stop()
            SpeechRecognizer.State.PROCESSING -> { /* 正在识别，忽略重复点击 */ }
        }
    }

    private fun render(state: SpeechRecognizer.State) {
        button.setText(
            when (state) {
                SpeechRecognizer.State.IDLE -> R.string.bar_voice
                SpeechRecognizer.State.LISTENING -> R.string.voice_recording
                SpeechRecognizer.State.PROCESSING -> R.string.voice_processing
            },
        )
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()

    private inner class Listener : SpeechRecognizer.Listener {
        override fun onStateChange(state: SpeechRecognizer.State) {
            activity.runOnUiThread { render(state) }
        }

        override fun onResult(text: String) {
            activity.runOnUiThread {
                val prev = input.text.toString()
                val sep = if (prev.isNotEmpty() && !prev.endsWith(" ") && !prev.endsWith("\n")) " " else ""
                input.setText(prev + sep + text)
                input.setSelection(input.text.length)
            }
        }

        override fun onPartial(text: String) { /* 不实时覆盖输入框，避免干扰用户手动编辑 */ }

        override fun onVolume(rmsdB: Float) {}

        override fun onError(code: Int) {
            activity.runOnUiThread {
                val res = when (code) {
                    SpeechRecognizer.Errors.PERMISSION -> R.string.voice_permission
                    SpeechRecognizer.Errors.UNAVAILABLE -> R.string.voice_unavailable
                    SpeechRecognizer.Errors.NETWORK -> R.string.voice_network
                    SpeechRecognizer.Errors.NO_MATCH -> R.string.voice_no_match
                    SpeechRecognizer.Errors.TIMEOUT -> R.string.voice_timeout
                    SpeechRecognizer.Errors.BUSY -> R.string.voice_busy
                    else -> R.string.voice_unavailable
                }
                Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
