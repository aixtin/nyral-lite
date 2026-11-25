package io.github.aixtin.nyrallite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.plugins.personastore.PersonaAvatar

/** 人设编辑页的头像：选图只暂存，保存人设时才落盘，取消编辑不留垃圾文件。 */
internal class PersonaAvatarPicker(
    private val activity: AppCompatActivity,
    private val cardId: String,
) {
    private var pending: ByteArray? = null
    private val gap = (activity.resources.displayMetrics.density * 12).toInt()

    private val preview = ImageView(activity).apply {
        val size = (activity.resources.displayMetrics.density * SIZE_DP).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size)
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundResource(R.drawable.bg_avatar)
        clipToOutline = true
        setOnClickListener { launcher.launch(arrayOf("image/*")) }
    }

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::read) }

    /** 编辑页里的一行：头像预览 + 选择入口。 */
    val view: View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, gap, 0, 0)
        addView(preview)
        addView(TextView(activity).apply {
            setText(R.string.persona_avatar_pick)
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.bar_action_text_muted))
            setPadding(gap, 0, 0, 0)
            setOnClickListener { launcher.launch(arrayOf("image/*")) }
        })
    }

    init {
        val file = PersonaAvatar.file(activity, cardId)
        if (file.isFile) decode(file.readBytes())?.let(preview::setImageBitmap)
    }

    /** 保存人设时调用：只有这次选过图才写文件。 */
    fun save() {
        pending?.let { PersonaAvatar.save(activity, cardId, it) }
    }

    private fun read(uri: android.net.Uri) {
        val bytes = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return
        pending = bytes
        decode(bytes)?.let(preview::setImageBitmap)
    }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private companion object {
        const val SIZE_DP = 60
    }
}
