package io.github.aixtin.nyrallite

import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.contract.ApiConfig
import io.github.aixtin.nyrallite.contract.ApiConfigStore
import io.github.aixtin.nyrallite.plugins.storageprefs.SharedPrefsApiConfigStore
import io.github.aixtin.nyrallite.kernel.get

/** 模型接入表单：地址、密钥、模型名与上下文预算。 */
internal class ModelForm(private val activity: AppCompatActivity) {

    private val inUrl = activity.findViewById<EditText>(R.id.in_url)
    private val inKey = activity.findViewById<EditText>(R.id.in_key)
    private val inModel = activity.findViewById<EditText>(R.id.in_model)
    private val inContext = activity.findViewById<EditText>(R.id.in_context)
    private val store = activity.kernel.services.get<ApiConfigStore>()

    fun load() {
        val config = store.load()
        // 未配置时显示占位提示而不是示例值，用户不用手动删默认值
        inUrl.setText(config.baseUrl.takeUnless { it == SharedPrefsApiConfigStore.DEF_URL }.orEmpty())
        inKey.setText(config.apiKey)
        inModel.setText(config.model.takeUnless { it == SharedPrefsApiConfigStore.DEF_MODEL }.orEmpty())
        // 0 表示默认值，界面上留空比显示 0 更直白
        inContext.setText(config.contextTokens.takeIf { it > 0 }?.toString().orEmpty())
    }

    /** 当前输入的端点地址，保存前用于明文警告判断。 */
    fun urlText(): String = inUrl.text.toString()

    /** 校验并保存；false 表示校验失败，错误已经提示过。 */
    fun save(): Boolean {
        val key = inKey.text.toString().trim()
        if (key.isBlank()) {
            toast(R.string.cfg_key_required)
            return false
        }
        val contextInput = inContext.text.toString().trim()
        val contextTokens = if (contextInput.isEmpty()) 0 else contextInput.toIntOrNull() ?: 0
        // 空 = 默认值；非空则必须落在可用区间，避免填 1 或 999999999 把请求撑爆
        if (contextInput.isNotEmpty() && contextTokens !in MIN_CONTEXT..MAX_CONTEXT) {
            toast(R.string.cfg_context_invalid)
            return false
        }
        store.save(
            ApiConfig(
                baseUrl = inUrl.text.toString().trim().ifBlank { SharedPrefsApiConfigStore.DEF_URL },
                apiKey = key,
                model = inModel.text.toString().trim().ifBlank { SharedPrefsApiConfigStore.DEF_MODEL },
                contextTokens = contextTokens,
            ),
        )
        return true
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()

    private companion object {
        /** 上下文预算的可用区间；默认值由 ContextWindow 决定。 */
        const val MIN_CONTEXT = 4_000
        const val MAX_CONTEXT = 2_000_000
    }
}
