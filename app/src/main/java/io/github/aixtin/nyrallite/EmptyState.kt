package io.github.aixtin.nyrallite

import android.graphics.BitmapFactory
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
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

    /** 消息数变化时调用：空会话显示（带浮现动效），有消息隐藏。 */
    fun sync() {
        val show = !hasMessages()
        val wasVisible = block.visibility == View.VISIBLE
        block.visibility = if (show) View.VISIBLE else View.GONE
        if (show && !wasVisible) {
            block.alpha = 0f
            block.translationY = 24f
            block.animate().alpha(1f).translationY(0f).setDuration(280L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** 人设变化时调用：刷新头像与名字，可见性不变。 */
    fun refresh(personaId: String?) {
        val card = personaId?.let { store?.get(it) }
        if (card == null) {
            title.text = activity.getString(R.string.app_name)
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            title.setTextColor(ContextCompat.getColor(activity, R.color.ai_text))
            avatar.setImageResource(R.drawable.ic_launcher)
            return
        }
        title.text = card.name
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
        title.setTextColor(ContextCompat.getColor(activity, R.color.brand))
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
