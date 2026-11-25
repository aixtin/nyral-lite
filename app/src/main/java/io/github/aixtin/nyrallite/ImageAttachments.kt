package io.github.aixtin.nyrallite

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import io.github.aixtin.nyrallite.contract.AttachmentRef
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.plugins.attachmentstore.ImageTranscoder
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 输入区图片附件：选图后立刻转码落盘，发送路径上不再做任何图像处理。
 * 缩略图展示交给 [ComposerAttachments]，这里只管选取、转码与生命周期。
 */
internal class ImageAttachments(
    private val activity: ComponentActivity,
    private val stores: AttachmentStores,
    private val onChanged: (List<AttachmentRef>) -> Unit,
) {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val pending = mutableListOf<AttachmentRef>()

    /** 系统 Photo Picker：无需读相册权限。 */
    private val picker = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris -> import(uris) }

    fun pick() {
        if (pending.size >= MAX_IMAGES) {
            toast(activity.getString(R.string.attach_limit))
            return
        }
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** 发送时整批取走并清空，缩略图条同步消失。 */
    fun take(): List<AttachmentRef> {
        if (pending.isEmpty()) return emptyList()
        val refs = pending.toList()
        pending.clear()
        onChanged(pending)
        return refs
    }

    fun removeAt(index: Int) {
        if (index !in pending.indices) return
        pending.removeAt(index)
        onChanged(pending)
    }

    fun dispose() = io.shutdownNow()

    private fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = MAX_IMAGES - pending.size
        if (room <= 0) {
            toast(activity.getString(R.string.attach_limit))
            return
        }
        if (uris.size > room) toast(activity.getString(R.string.attach_limit))
        val picked = uris.take(room)
        io.execute {
            val added = ArrayList<AttachmentRef>(picked.size)
            var error: String? = null
            for (uri in picked) {
                runCatching {
                    val jpeg = ImageTranscoder.transcode(readBytes(uri))
                    stores.put(jpeg, "image/jpeg", uri.lastPathSegment ?: "image.jpg")
                }.onSuccess { added += it }.onFailure { error = it.message ?: "未知错误" }
            }
            activity.runOnUiThread {
                pending += added
                onChanged(pending)
                error?.let {
                    toast(activity.getString(R.string.attach_failed, it.take(60)), long = true)
                }
            }
        }
    }

    /** 读取 URI 字节，超过转码上限立即中止，避免把超大文件读进内存。 */
    private fun readBytes(uri: Uri): ByteArray {
        val input = activity.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= ImageTranscoder.MAX_SOURCE_BYTES) { "图片超过 20MB" }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(activity, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            .show()
    }

    private companion object {
        /** 单条消息最多 4 张图：再多会让请求体积和一帧的绘制都失控。 */
        const val MAX_IMAGES = 4
    }
}
