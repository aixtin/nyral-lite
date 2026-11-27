package io.github.aixtin.nyrallite

import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.personastore.PersonaAvatar

/**
 * 空会话状态：有人设时显示头像 + 名字，无人设时回落到 App 名。
 * 可见性由消息列表驱动（适配器数据观察），人设变化由外部调用 [refresh]。
 */
internal class EmptyState(
    private val activity: AppCompatActivity,
    private val block: View,
    private val avatar: ImageView,
    private val title: TextView,
    private val hasMessages: () -> Boolean,
) {
    private val store = activity.kernel.services.getOrNull<PersonaStore>()

    /** 消息数变化时调用：空会话显示，有消息隐藏。 */
    fun sync() {
        block.visibility = if (hasMessages()) View.GONE else View.VISIBLE
    }

    /** 人设变化时调用：刷新头像与名字，可见性不变。 */
    fun refresh(personaId: String?) {
        val card = personaId?.let { store?.get(it) }
        if (card == null) {
            title.text = activity.getString(R.string.app_name)
            avatar.setImageResource(R.drawable.ic_launcher)
            return
        }
        title.text = card.name
        val file = PersonaAvatar.file(activity, card.id)
        if (file.isFile) {
            // 原图较大，空状态显示 72dp，降采样即可
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            avatar.setImageBitmap(BitmapFactory.decodeFile(file.path, options))
        } else {
            avatar.setImageResource(R.drawable.ic_launcher)
        }
    }

    companion object {
        /** 从主界面布局取控件，并挂上适配器数据观察驱动可见性。 */
        fun attach(
            activity: AppCompatActivity,
            adapter: RecyclerView.Adapter<*>,
            hasMessages: () -> Boolean,
        ): EmptyState {
            val state = EmptyState(
                activity,
                activity.findViewById(R.id.empty_state),
                activity.findViewById(R.id.empty_avatar),
                activity.findViewById(R.id.empty_title),
                hasMessages,
            )
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = state.sync()
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = state.sync()
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = state.sync()
            })
            return state
        }
    }
}
