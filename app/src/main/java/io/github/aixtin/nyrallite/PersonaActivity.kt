package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.plugins.personastore.PersonaAvatar
import io.github.aixtin.nyrallite.plugins.personastore.PngCardReader
import io.github.aixtin.nyrallite.plugins.personastore.TavernCardParser

/** 人设库：SAF 导入、短长档位、编辑与全局默认选择。 */
class PersonaActivity : AppCompatActivity() {

    private lateinit var store: PersonaStore
    private val items = mutableListOf<PersonaCard>()
    private var currentId: String? = null
    private lateinit var adapter: PersonaAdapter
    private lateinit var empty: TextView

    private val importer = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::importCard) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_persona)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        store = kernel.services.get()
        currentId = intent.getStringExtra(EXTRA_CURRENT_ID)
        empty = findViewById(R.id.persona_empty)
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.persona_new).setOnClickListener { openEditor(null) }
        findViewById<TextView>(R.id.persona_import).setOnClickListener {
            importer.launch(arrayOf("*/*"))
        }
        findViewById<TextView>(R.id.persona_disable).setOnClickListener { select(null) }
        adapter = PersonaAdapter(
            items = items,
            currentId = { currentId },
            defaultId = { store.defaultId },
            onUse = { select(it.id) },
            onMode = { card, mode -> store.setMode(card.id, mode); reload() },
            onEdit = { card -> openEditor(card.id) },
            onDelete = ::confirmDelete,
        )
        findViewById<RecyclerView>(R.id.persona_list).apply {
            layoutManager = LinearLayoutManager(this@PersonaActivity)
            adapter = this@PersonaActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) reload()
    }

    private fun reload() {
        items.clear()
        items += store.all()
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** id 为空表示新建：不落盘，保存时才写入人设库，返回则不留空卡。 */
    private fun openEditor(id: String?) {
        startActivity(
            Intent(this, PersonaEditActivity::class.java)
                .putExtra(PersonaEditActivity.EXTRA_ID, id),
        )
    }

    private fun importCard(uri: android.net.Uri) {
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取文件")
            val png = PngCardReader.isPng(bytes)
            val text = if (png) {
                PngCardReader.jsonText(bytes) ?: error("PNG 里没有角色卡数据")
            } else {
                String(bytes, Charsets.UTF_8)
            }
            val card = TavernCardParser.parse(text)
            store.upsert(card)
            // PNG 卡的原图就是头像，按 id 一起落盘；纯 JSON 卡没有图
            if (png) PersonaAvatar.save(this, card.id, bytes)
        }.onSuccess {
            reload()
            Toast.makeText(this, R.string.persona_imported, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.persona_import_failed, it.message.orEmpty().take(80)),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** 选择即设为全局默认并返回主界面；是否新开旧会话由主界面确认。 */
    private fun select(id: String?) {
        store.setDefault(id)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PERSONA_ID, id))
        finish()
    }

    private fun confirmDelete(card: PersonaCard) {
        if (card.id == currentId) {
            Toast.makeText(this, R.string.persona_delete_current, Toast.LENGTH_SHORT).show()
            return
        }
        AppDialogs.confirmDelete(
            this,
            getString(R.string.persona_delete_confirm, card.name),
            R.string.persona_delete,
        ) {
            store.delete(card.id)
            PersonaAvatar.delete(this, card.id)
            reload()
        }
    }

    companion object {
        const val EXTRA_CURRENT_ID = "current_persona_id"
        const val EXTRA_PERSONA_ID = "persona_id"
    }
}
