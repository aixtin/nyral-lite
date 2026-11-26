package io.github.aixtin.nyrallite

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 统一确认弹窗：删除类操作的正向按钮用告警色，避免和品牌蓝混淆。 */
internal object AppDialogs {

    fun confirmDelete(
        activity: Activity,
        message: String,
        confirmRes: Int,
        onConfirm: () -> Unit,
    ) {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setMessage(message)
            .setNegativeButton(R.string.history_cancel, null)
            .setPositiveButton(confirmRes) { _, _ -> onConfirm() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(activity, R.color.tool_fail))
        }
        dialog.show()
    }
}
