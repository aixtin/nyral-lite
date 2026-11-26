package io.github.aixtin.nyrallite

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.MemoryEntry
import io.github.aixtin.nyrallite.contract.MemoryStore
import io.github.aixtin.nyrallite.kernel.get

/**
 * 长期记忆列表：看得见、删得掉。
 * 置顶条目排最前，其次是时间倒序；删除立即落盘，下一轮请求就不再注入。
 */
class MemoryActivity : AppCompatActivity() {

    private val items = mutableListOf<MemoryEntry>()
    private lateinit var store: MemoryStore
    private lateinit var adapter: MemoryAdapter
    private lateinit var hint: TextView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        store = kernel.services.get<MemoryStore>()
        hint = findViewById(R.id.memory_hint)
        empty = findViewById(R.id.memory_empty)
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        adapter = MemoryAdapter(items, onDelete = { confirmDelete(it) }, onPin = { togglePin(it) })
        val list = findViewById<RecyclerView>(R.id.memory_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        reload()
    }

    private fun reload() {
        items.clear()
        items += store.all().sortedWith(
            compareByDescending<MemoryEntry> { it.pinned }.thenByDescending { it.ts },
        )
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        hint.text = getString(R.string.memory_hint_count, items.size)
    }

    private fun togglePin(entry: MemoryEntry) {
        store.setPinned(entry.id, !entry.pinned)
        reload()
    }

    private fun confirmDelete(entry: MemoryEntry) {
        AppDialogs.confirmDelete(
            this,
            getString(R.string.memory_delete_confirm, entry.title),
            R.string.memory_delete,
        ) {
            store.delete(entry.id)
            reload()
        }
    }
}
