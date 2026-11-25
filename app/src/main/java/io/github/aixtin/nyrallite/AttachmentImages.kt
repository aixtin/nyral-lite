package io.github.aixtin.nyrallite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import io.github.aixtin.nyrallite.contract.AttachmentRef
import java.util.concurrent.Executors

/**
 * 附件解码：后台线程按需解码到指定尺寸，主线程回调。
 * 不引第三方图片库——缩略图和全屏查看共用这一条路径。
 *
 * 解码结果按附件 key + 目标尺寸做内存缓存：列表滚动/键盘抬起时重新绑定可见条目，
 * 直接命中缓存即可同步出图，不会先闪一下占位底色再"啪"地出现（图片消息的残影来源）。
 */
internal object AttachmentImages {

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** 缓存上限：约 8MB，够放十几张缩略图，超出淘汰最久未用的。 */
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    /** 按字节数计量的缩略图缓存，超出自动淘汰最久未用的。 */
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(
        ref: AttachmentRef,
        open: (AttachmentRef) -> ByteArray?,
        maxPx: Int,
        onReady: (Bitmap?) -> Unit,
    ) {
        val cacheKey = "${ref.key}@$maxPx"
        cache.get(cacheKey)?.let {
            onReady(it)
            return
        }
        executor.execute {
            val bitmap = runCatching { open(ref)?.let { decode(it, maxPx) } }.getOrNull()
            main.post {
                if (bitmap != null) cache.put(cacheKey, bitmap)
                onReady(bitmap)
            }
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
