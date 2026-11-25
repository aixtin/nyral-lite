package io.github.aixtin.nyrallite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.personastore.PersonaAvatar
import java.io.File

/**
 * 标题栏的人设位：有人设时显示头像 + 小字名字，无人设时回落到 App 名。
 * 两种情况共用一个位置，避免 App 名与角色名同时出现。
 */
internal class PersonaHeader(
    private val activity: AppCompatActivity,
    private val appTitle: TextView,
    private val block: View,
    private val avatar: ImageView,
    private val name: TextView,
) {
    private val store = activity.kernel.services.getOrNull<PersonaStore>()

    fun refresh(personaId: String?) {
        val card = personaId?.let { store?.get(it) }
        if (card == null) {
            block.visibility = View.GONE
            appTitle.visibility = View.VISIBLE
            return
        }
        val bitmap = decode(PersonaAvatar.file(activity, card.id))
        appTitle.visibility = View.GONE
        block.visibility = View.VISIBLE
        avatar.setImageBitmap(bitmap)
        avatar.visibility = if (bitmap == null) View.GONE else View.VISIBLE
        name.text = card.name
        // 纯 JSON 卡没有头像图：把名字放大回标题大小，避免只剩一行小字
        name.textSize = if (bitmap == null) 16f else 10f
        name.setTextColor(
            ContextCompat.getColor(
                activity,
                if (bitmap == null) R.color.ai_text else R.color.bar_action_text_muted,
            ),
        )
    }

    private fun decode(file: File): Bitmap? {
        if (!file.isFile) return null
        // 原图 400×600，显示只有 28dp，降采样一半即可
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        return BitmapFactory.decodeFile(file.path, options)
    }

    companion object {
        /** 从主界面布局取控件并挂上。 */
        fun attach(activity: AppCompatActivity) = PersonaHeader(
            activity,
            activity.findViewById(R.id.title_bar),
            activity.findViewById(R.id.header_persona),
            activity.findViewById(R.id.header_avatar),
            activity.findViewById(R.id.header_name),
        )
    }
}
