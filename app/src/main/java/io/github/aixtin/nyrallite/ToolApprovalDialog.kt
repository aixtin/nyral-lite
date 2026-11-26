package io.github.aixtin.nyrallite

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aixtin.nyrallite.contract.ApprovalDecision
import io.github.aixtin.nyrallite.contract.ToolApproval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * [ToolApproval] 的界面对现：用系统对话框询问用户，挂起直到选择。
 *
 * 只持有前台 Activity 的弱引用；没有界面时返回 UNAVAILABLE，
 * 由安全插件按拒绝处理。界面不复制母体，保持最简三按钮。
 */
class ToolApprovalDialog : ToolApproval {

    private var host: WeakReference<Activity>? = null

    /** 由 Activity 在 onCreate 时登记。 */
    fun attach(activity: Activity) {
        host = WeakReference(activity)
    }

    /** Activity 销毁时注销；只清自己，避免误伤新 Activity。 */
    fun detach(activity: Activity) {
        if (host?.get() === activity) host = null
    }

    override suspend fun ask(
        pluginId: String,
        tool: String,
        arguments: String,
    ): ApprovalDecision = withContext(Dispatchers.Main) {
        val activity = host?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return@withContext ApprovalDecision.UNAVAILABLE

        suspendCancellableCoroutine { cont ->
            var settled = false
            fun settle(decision: ApprovalDecision) {
                if (settled) return
                settled = true
                if (cont.isActive) cont.resume(decision)
            }

            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.approval_title, tool))
                .setMessage(activity.getString(R.string.approval_message, pluginId, arguments.take(300)))
                .setPositiveButton(R.string.approval_allow_once) { _, _ -> settle(ApprovalDecision.ALLOW_ONCE) }
                .setNeutralButton(R.string.approval_allow_session) { _, _ -> settle(ApprovalDecision.ALLOW_SESSION) }
                .setNegativeButton(R.string.approval_deny) { _, _ -> settle(ApprovalDecision.DENY) }
                .setOnCancelListener { settle(ApprovalDecision.DENY) }
                .create()

            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener { settle(ApprovalDecision.DENY) }
            try {
                dialog.show()
            } catch (e: Exception) {
                settle(ApprovalDecision.UNAVAILABLE)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { activity.runOnUiThread { dialog.dismiss() } }
        }
    }
}
