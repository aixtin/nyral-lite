package io.github.aixtin.nyrallite

import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.SecuritySettings
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.kernel.get

/** 安全门禁表单：档位、白名单与禁止名单。 */
internal class SecurityForm(private val activity: AppCompatActivity) {

    private val spMode = activity.findViewById<Spinner>(R.id.sp_security_mode)
    private val inAllow = activity.findViewById<EditText>(R.id.in_allow_tools)
    private val inDeny = activity.findViewById<EditText>(R.id.in_deny_tools)
    private val store = activity.kernel.services.get<SecuritySettingsStore>()

    /** 档位下拉与枚举一一对应，避免位置写死。 */
    private val modes = SecurityMode.entries

    fun load() {
        val security = store.load()
        inAllow.setText(security.allowTools)
        inDeny.setText(security.denyTools)
        SpinnerStyle.install(spMode, modes.map { activity.getString(modeLabel(it)) })
        spMode.setSelection(modes.indexOf(security.mode).coerceAtLeast(0))
    }

    fun save() = store.save(
        SecuritySettings(
            mode = modes[spMode.selectedItemPosition.coerceIn(0, modes.lastIndex)],
            allowTools = inAllow.text.toString(),
            denyTools = inDeny.text.toString(),
        ),
    )

    private fun modeLabel(mode: SecurityMode): Int = when (mode) {
        SecurityMode.OFF -> R.string.cfg_security_off
        SecurityMode.AUTO -> R.string.cfg_security_auto
        SecurityMode.STRICT -> R.string.cfg_security_strict
    }
}
