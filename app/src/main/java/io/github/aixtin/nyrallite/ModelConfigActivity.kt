package io.github.aixtin.nyrallite

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 模型接入设置页：地址、密钥、模型名与上下文预算。 */
class ModelConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_config)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val form = ModelForm(this)
        form.load()
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            if (form.save()) finish()
        }
    }
}
