package io.github.aixtin.nyrallite

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 人设切换：已有消息时优先隔离到新会话，避免两种人设混进同一段历史。
 * 人设页只负责选，落地动作（新开会话 / 仅后续生效）在这里。
 */
internal class PersonaSwitcher(
    private val activity: AppCompatActivity,
    private val sessions: SessionController,
    private val onLoad: (List<Message>) -> Unit,
    private val restoreDraft: () -> Unit,
    private val hasMessages: () -> Boolean,
) {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onSelected(result.data?.getStringExtra(PersonaActivity.EXTRA_PERSONA_ID))
        }
    }

    fun launch(currentId: String?) {
        launcher.launch(
            Intent(activity, PersonaActivity::class.java)
                .putExtra(PersonaActivity.EXTRA_CURRENT_ID, currentId),
        )
    }

    private fun onSelected(id: String?) {
        if (id == sessions.currentPersonaId) return
        if (!hasMessages()) {
            apply(id, newSession = false)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.persona_title)
            .setMessage(R.string.persona_switch_message)
            .setPositiveButton(R.string.persona_new_session) { _, _ ->
                apply(id, newSession = true)
            }
            .setNegativeButton(R.string.persona_keep_session) { _, _ ->
                apply(id, newSession = false)
            }
            .show()
    }

    private fun apply(id: String?, newSession: Boolean) {
        if (newSession) {
            sessions.newSession(onLoad)
            restoreDraft()
        } else {
            sessions.setPersona(id)
        }
        Toast.makeText(
            activity,
            if (id == null) R.string.persona_disabled else R.string.persona_changed,
            Toast.LENGTH_SHORT,
        ).show()
    }
}
