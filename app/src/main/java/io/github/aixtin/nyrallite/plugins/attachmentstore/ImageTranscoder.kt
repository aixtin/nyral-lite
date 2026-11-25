package io.github.aixtin.nyrallite.plugins.attachmentstore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 本地图片转码：长边压到 1568、JPEG 重编码（顺带清掉 EXIF/GPS）、目标 ≤700KB。
 * 只在选图时跑一次，发送时直接读转码后的字节，不在请求路径上做图像处理。
 */
object ImageTranscoder {

    /** 超过此大小的源文件直接拒绝，避免解码时 OOM。 */
    const val MAX_SOURCE_BYTES = 20 * 1024 * 1024

    private const val MAX_EDGE = 1568
    private const val TARGET_BYTES = 700 * 1024
    private val QUALITIES = intArrayOf(82, 72, 62)

    fun transcode(source: ByteArray): ByteArray {
        require(source.size <= MAX_SOURCE_BYTES) { "图片超过 20MB" }
        val rotation = rotationOf(source)
        val decoded = decodeSampled(source) ?: error("无法识别该图片")
        val scaled = scaleAndRotate(decoded, rotation)
        if (scaled !== decoded) decoded.recycle()
        return try {
            encode(scaled)
        } finally {
            scaled.recycle()
        }
    }

    /** 先读尺寸算采样率再解码，12MP 原图也不会整张进内存。 */
    private fun decodeSampled(source: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(source, 0, source.size, opts)
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val matrix = Matrix()
        val scale = MAX_EDGE.toFloat() / maxOf(src.width, src.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (matrix.isIdentity) return src
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** 从高质量往下降；仍超限就缩一半再来一轮。 */
    private fun encode(bitmap: Bitmap): ByteArray {
        var current = bitmap
        var owned = false
        try {
            for (pass in 0..1) {
                for (quality in QUALITIES) {
                    val out = ByteArrayOutputStream()
                    current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val bytes = out.toByteArray()
                    if (bytes.size <= TARGET_BYTES) return bytes
                }
                if (pass == 0) {
                    val half = Bitmap.createScaledBitmap(
                        current,
                        (current.width / 2).coerceAtLeast(1),
                        (current.height / 2).coerceAtLeast(1),
                        true,
                    )
                    if (owned) current.recycle()
                    current = half
                    owned = true
                }
            }
            val out = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.JPEG, 50, out)
            return out.toByteArray()
        } finally {
            if (owned) current.recycle()
        }
    }

    private fun rotationOf(source: ByteArray): Int = runCatching {
        val orientation = ExifInterface(ByteArrayInputStream(source))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
