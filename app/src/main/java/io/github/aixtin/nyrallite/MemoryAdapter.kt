package io.github.aixtin.nyrallite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.MemoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 记忆列表：标题 + 时间 + 正文预览，右侧置顶与删除。排序在 Activity 里做。 */
internal class MemoryAdapter(
    private val items: List<MemoryEntry>,
    private val onDelete: (MemoryEntry) -> Unit,
    private val onPin: (MemoryEntry) -> Unit,
) : RecyclerView.Adapter<MemoryAdapter.Holder>() {

    private val dates = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.memory_title)
        val meta: TextView = v.findViewById(R.id.memory_meta)
        val text: TextView = v.findViewById(R.id.memory_text)
        val pin: TextView = v.findViewById(R.id.memory_pin)
        val delete: TextView = v.findViewById(R.id.memory_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.title.text = entry.title
        val time = dates.format(Date(entry.ts))
        holder.meta.text = if (entry.pinned) {
            holder.itemView.context.getString(R.string.memory_pinned_meta, time)
        } else {
            time
        }
        holder.text.text = entry.text
        holder.pin.text = holder.itemView.context.getString(
            if (entry.pinned) R.string.memory_unpin else R.string.memory_pin,
        )
        holder.pin.setOnClickListener { onPin(entry) }
        holder.delete.setOnClickListener { onDelete(entry) }
    }

    override fun getItemCount(): Int = items.size
}
