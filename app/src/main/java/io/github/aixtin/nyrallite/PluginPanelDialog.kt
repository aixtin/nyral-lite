package io.github.aixtin.nyrallite

import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aixtin.nyrallite.kernel.PanelSpec
import io.github.aixtin.nyrallite.kernel.PluginConfig

/** 宿主统一渲染的插件配置对话框：点保存即写配置，插件下次读取时热生效。 */
internal object PluginPanelDialog {

    fun show(
        activity: AppCompatActivity,
        pluginId: String,
        spec: PanelSpec,
        config: PluginConfig,
        onSaved: () -> Unit,
    ) {
        val form = PluginPanelView(activity, spec, config)
        val scroll = ScrollView(activity).apply { addView(form.root) }
        MaterialAlertDialogBuilder(activity)
            .setTitle(spec.title.ifBlank { "$pluginId 配置" })
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                form.save()
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
