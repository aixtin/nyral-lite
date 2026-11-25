package io.github.aixtin.nyrallite

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.PersonaCard
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.get
import java.util.UUID

/** 人设查看与编辑：字段名称对应角色卡来源，保存后下一轮请求生效。 */
class PersonaEditActivity : AppCompatActivity() {

    private lateinit var store: PersonaStore
    private lateinit var original: PersonaCard
    private lateinit var name: EditText
    private lateinit var system: EditText
    private lateinit var personality: EditText
    private lateinit var scenario: EditText
    private lateinit var description: EditText
    private lateinit var example: EditText
    private lateinit var voice: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = kernel.services.get()
        val id = intent.getStringExtra(EXTRA_ID)
        // 无 id 表示新建：空卡先放内存，保存时才落盘，中途返回不留空卡
        original = if (id == null) {
            PersonaCard(id = UUID.randomUUID().toString(), name = "")
        } else {
            store.get(id) ?: run { finish(); return }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        content.addView(label(getString(if (id == null) R.string.persona_create_title else R.string.persona_edit_title), 20f))
        name = content.field(R.string.persona_field_name, original.name, 1)
        name.hint = getString(R.string.persona_name_hint)
        content.addView(label(getString(R.string.persona_short)))
        system = content.field(R.string.persona_field_system, original.systemPrompt)
        personality = content.field(R.string.persona_field_personality, original.personality)
        scenario = content.field(R.string.persona_field_scenario, original.scenario)
        content.addView(label(getString(R.string.persona_long)))
        description = content.field(R.string.persona_field_description, original.description)
        example = content.field(R.string.persona_field_example, original.exampleDialogue)
        voice = content.field(R.string.persona_field_voice, original.voiceContent)
        content.addView(Button(this).apply {
            text = getString(R.string.persona_save)
            setOnClickListener { save() }
        })
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@PersonaEditActivity, R.color.bg))
            addView(content)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun save() {
        val cardName = name.text.toString().trim()
        if (cardName.isEmpty()) {
            Toast.makeText(this, R.string.persona_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        store.upsert(
            original.copy(
                name = cardName,
                systemPrompt = system.text.toString().trim(),
                personality = personality.text.toString().trim(),
                scenario = scenario.text.toString().trim(),
                description = description.text.toString().trim(),
                exampleDialogue = example.text.toString().trim(),
                voiceContent = voice.text.toString().trim(),
            ),
        )
        Toast.makeText(this, R.string.persona_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun LinearLayout.field(labelRes: Int, value: String, lines: Int = 3): EditText {
        addView(label(getString(labelRes), 13f))
        return EditText(this@PersonaEditActivity).apply {
            setText(value)
            textSize = 14f
            minLines = lines
            gravity = Gravity.TOP or Gravity.START
            setTextColor(ContextCompat.getColor(context, R.color.ai_text))
            setHintTextColor(ContextCompat.getColor(context, R.color.bar_action_text_muted))
            setBackgroundColor(Color.TRANSPARENT)
        }.also(::addView)
    }

    private fun label(text: String, size: Float = 16f) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(ContextCompat.getColor(context, R.color.ai_text))
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ID = "persona_edit_id"
    }
}
