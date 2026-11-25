package io.github.aixtin.nyrallite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaMode
import io.github.aixtin.nyrallite.plugins.personastore.PersonaPrompt

/** 人设卡片列表：当前会话与全局默认分别标记，避免把两种状态混为一谈。 */
internal class PersonaAdapter(
    private val items: List<PersonaCard>,
    private val currentId: () -> String?,
    private val defaultId: () -> String?,
    private val onUse: (PersonaCard) -> Unit,
    private val onMode: (PersonaCard, PersonaMode) -> Unit,
    private val onEdit: (PersonaCard) -> Unit,
    private val onDelete: (PersonaCard) -> Unit,
) : RecyclerView.Adapter<PersonaAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.persona_name)
        val state: TextView = v.findViewById(R.id.persona_state)
        val preview: TextView = v.findViewById(R.id.persona_preview)
        val short: TextView = v.findViewById(R.id.persona_short)
        val long: TextView = v.findViewById(R.id.persona_long)
        val use: TextView = v.findViewById(R.id.persona_use)
        val edit: TextView = v.findViewById(R.id.persona_edit)
        val delete: TextView = v.findViewById(R.id.persona_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_persona, parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        val current = card.id == currentId()
        val default = card.id == defaultId()
        holder.name.text = card.name
        holder.state.text = when {
            current && default -> holder.itemView.context.getString(R.string.persona_current_default)
            current -> holder.itemView.context.getString(R.string.persona_current)
            default -> holder.itemView.context.getString(R.string.persona_default)
            else -> ""
        }
        holder.preview.text = PersonaPrompt.block(card).trim().take(PREVIEW_CHARS)
        holder.preview.visibility = if (holder.preview.text.isEmpty()) View.GONE else View.VISIBLE
        tintMode(holder.short, card.mode == PersonaMode.SHORT)
        tintMode(holder.long, card.mode == PersonaMode.LONG)
        holder.short.setOnClickListener { onMode(card, PersonaMode.SHORT) }
        holder.long.setOnClickListener { onMode(card, PersonaMode.LONG) }
        holder.use.setOnClickListener { onUse(card) }
        holder.edit.setOnClickListener { onEdit(card) }
        holder.delete.setOnClickListener { onDelete(card) }
    }

    private fun tintMode(view: TextView, selected: Boolean) {
        val color = if (selected) R.color.brand else R.color.bar_action_text_muted
        view.setTextColor(ContextCompat.getColor(view.context, color))
    }

    override fun getItemCount(): Int = items.size

    private companion object {
        const val PREVIEW_CHARS = 240
    }
}
