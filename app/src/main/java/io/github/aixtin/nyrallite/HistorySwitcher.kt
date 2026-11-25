package io.github.aixtin.nyrallite

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** 历史会话切换：打开历史页，按返回值新建或打开会话。 */
internal class HistorySwitcher(
    private val activity: AppCompatActivity,
    private val sessions: SessionController,
    private val onLoad: (List<Message>) -> Unit,
    private val afterSwitch: () -> Unit,
) {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        if (data.getBooleanExtra(HistoryActivity.EXTRA_NEW, false)) {
            sessions.newSession(onLoad)
        } else {
            val id = data.getStringExtra(HistoryActivity.EXTRA_ID)
                ?: return@registerForActivityResult
            sessions.open(id, onLoad)
        }
        afterSwitch()
    }

    fun launch() {
        launcher.launch(Intent(activity, HistoryActivity::class.java))
    }
}
