package io.github.aixtin.nyrallite.plugins.modelopenai

import android.util.Base64
import io.github.aixtin.nyrallite.contract.AttachmentRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多模态 content：有附件时从字符串变成 parts 数组（text + image_url）。
 * 附件字节在传输线程里读出来转 base64，主线程不参与。
 */
internal object ContentParts {

    fun contentOf(
        text: String,
        attachments: List<AttachmentRef>,
        open: (AttachmentRef) -> ByteArray?,
    ): Any {
        if (attachments.isEmpty()) return text
        val parts = JSONArray()
        if (text.isNotBlank()) {
            parts.put(JSONObject().put("type", "text").put("text", text))
        }
        for (ref in attachments) {
            if (!ref.mime.startsWith("image/")) continue
            val bytes = open(ref) ?: continue
            val url = "data:${ref.mime};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            parts.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", url)),
            )
        }
        // 附件全部读失败时退回纯文本，避免发出空数组
        return if (parts.length() == 0) text else parts
    }
}
