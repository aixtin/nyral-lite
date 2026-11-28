package io.github.aixtin.nyrallite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 关于页：版本、构建信息与开源信息，只读展示。 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<TextView>(R.id.txt_version).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        findViewById<TextView>(R.id.txt_build).text =
            getString(R.string.about_build, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME)
        findViewById<View>(R.id.card_repo).setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_repo_url))),
                )
            }
        }
        bindLink(R.id.row_site, R.string.about_site_url)
        bindLink(R.id.row_blog, R.string.about_blog_url)
        bindLink(R.id.row_group, R.string.about_group_url)
    }

    // 社区行点击跳浏览器；占位链接打不开时静默忽略
    private fun bindLink(viewId: Int, urlRes: Int) {
        findViewById<View>(viewId).setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlRes))))
            }
        }
    }
}
