package io.github.aixtin.nyrallite

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.kernel.get

/** 全屏查看单张附件：黑底、按屏幕长边解码，点击退出。 */
class AttachmentViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ref = intent.toRef()
        if (ref == null) {
            finish()
            return
        }
        val stores = kernel.services.get<AttachmentStores>()
        val image = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        setContentView(image)
        val maxPx = resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
        AttachmentImages.load(ref, stores::open, maxPx) { bitmap ->
            // 解码失败（文件被 GC 或已删除）直接退出，不留黑屏
            if (bitmap == null) finish() else image.setImageBitmap(bitmap)
        }
    }

    private fun Intent.toRef(): AttachmentRef? {
        val key = getStringExtra(EXTRA_KEY) ?: return null
        return AttachmentRef(
            store = getStringExtra(EXTRA_STORE).orEmpty(),
            key = key,
            mime = getStringExtra(EXTRA_MIME).orEmpty(),
            name = getStringExtra(EXTRA_NAME).orEmpty(),
        )
    }

    companion object {
        private const val EXTRA_STORE = "store"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_NAME = "name"

        fun intent(context: Context, ref: AttachmentRef): Intent =
            Intent(context, AttachmentViewerActivity::class.java)
                .putExtra(EXTRA_STORE, ref.store)
                .putExtra(EXTRA_KEY, ref.key)
                .putExtra(EXTRA_MIME, ref.mime)
                .putExtra(EXTRA_NAME, ref.name)
    }
}
