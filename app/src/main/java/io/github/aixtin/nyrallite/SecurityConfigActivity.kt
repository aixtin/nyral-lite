package io.github.aixtin.nyrallite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aixtin.nyrallite.contract.ToolAuditLog
import io.github.aixtin.nyrallite.kernel.getOrNull
import java.io.File

/** 安全门禁设置页：档位、白名单、禁止名单与审计导出。 */
class SecurityConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_config)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, maxOf(bars.bottom, ime.bottom))
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val form = SecurityForm(this)
        form.load()

        findViewById<Button>(R.id.btn_export_audit).setOnClickListener {
            val file = kernel.services.getOrNull<ToolAuditLog>()?.file()
            if (file == null) {
                Toast.makeText(this, R.string.cfg_audit_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareAudit(file)
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            form.save()
            finish()
        }
    }

    /** 分享审计日志文件；FileProvider 只暴露 security/ 子目录。 */
    private fun shareAudit(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.cfg_audit_share_title)))
    }
}
