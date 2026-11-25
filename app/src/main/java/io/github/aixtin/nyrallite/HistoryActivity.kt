package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.SessionSummary
import io.github.aixtin.nyrallite.kernel.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史会话页：列出本地会话，点击继续聊天，右侧删除；顶部可新建空会话。
 * 只回传选择结果，真正的会话加载由主界面完成。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var store: SessionStore
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private val timeFormat = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        store = kernel.services.get<SessionStore>()
        list = findViewById(R.id.session_list)
        empty = findViewById(R.id.session_empty)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_new).setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_NEW, true))
            finish()
        }
        render()
    }

    private fun render() {
        val sessions = store.list()
        list.removeAllViews()
        empty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        sessions.forEach { list.addView(row(it)) }
    }

    private fun row(session: SessionSummary): View {
        val view = layoutInflater.inflate(R.layout.item_session, list, false)
        view.findViewById<TextView>(R.id.session_title).text = session.title
        view.findViewById<TextView>(R.id.session_time).text =
            timeFormat.format(Date(session.updatedAt))
        view.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_ID, session.id))
            finish()
        }
        view.findViewById<TextView>(R.id.session_delete).setOnClickListener {
            confirmDelete(session)
        }
        return view
    }

    private fun confirmDelete(session: SessionSummary) {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_delete_confirm)
            .setNegativeButton(R.string.history_cancel, null)
            .setPositiveButton(R.string.history_delete) { _, _ ->
                store.delete(session.id)
                render()
            }
            .show()
    }

    companion object {
        /** 回传：继续聊天的会话 id。 */
        const val EXTRA_ID = "extra_session_id"

        /** 回传：用户选择了新建空会话。 */
        const val EXTRA_NEW = "extra_session_new"

        private const val TIME_PATTERN = "MM-dd HH:mm"
    }
}
