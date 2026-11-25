package io.github.aixtin.nyrallite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import io.github.aixtin.nyrallite.contract.AttachmentRef
import java.util.concurrent.Executors

/**
 * 附件解码：后台线程按需解码到指定尺寸，主线程回调。
 * 不引第三方图片库——缩略图和全屏查看共用这一条路径。
 */
internal object AttachmentImages {

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(
        ref: AttachmentRef,
        open: (AttachmentRef) -> ByteArray?,
        maxPx: Int,
        onReady: (Bitmap?) -> Unit,
    ) {
        executor.execute {
            val bitmap = runCatching { open(ref)?.let { decode(it, maxPx) } }.getOrNull()
            main.post { onReady(bitmap) }
        }
    }

    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}
