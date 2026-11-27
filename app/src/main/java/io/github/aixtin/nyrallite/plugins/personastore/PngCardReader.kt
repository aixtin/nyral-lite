package io.github.aixtin.nyrallite.plugins.personastore

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * 角色卡 PNG 读取：SillyTavern / Chub 把卡塞进 PNG 的文本块里，
 * key 通常是 chara（V3 也用 ccv3），值是 base64 编码的 JSON。
 * 只做解包，字段解析仍交给 [TavernCardParser]。
 */
object PngCardReader {

    /** 是否是 PNG：靠文件头判断，避免把二进制当文本解析。 */
    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    /** 取出内嵌的角色卡 JSON；不是 PNG 或没有卡数据时返回 null。 */
    fun jsonText(bytes: ByteArray): String? {
        if (!isPng(bytes)) return null
        val texts = mutableListOf<String>()
        var pos = SIGNATURE.size
        while (pos + 8 <= bytes.size) {
            val length = readInt(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val start = pos + 8
            if (length < 0 || start + length + 4 > bytes.size) break
            if (type == "tEXt" || type == "zTXt" || type == "iTXt") {
                chunkText(type, bytes, start, length)?.let(texts::add)
            }
            if (type == "IEND") break
            pos = start + length + 4
        }
        // Chub 会写两段；从后往前取第一段像 JSON 的
        return texts.asReversed().firstOrNull {
            it.trimStart().removePrefix("\uFEFF").startsWith("{")
        }
    }

    /** 解一个文本块：先看 key，再按类型解压缩与 base64。 */
    private fun chunkText(type: String, bytes: ByteArray, start: Int, length: Int): String? {
        val end = start + length
        val keyEnd = skipField(bytes, start, end) ?: return null
        val key = String(bytes, start, keyEnd - start - 1, Charsets.ISO_8859_1).lowercase()
        if (key !in KEYS) return null
        val body = when (type) {
            // zTXt：key\0 方法(1) 压缩文本
            "zTXt" -> inflate(bytes, keyEnd + 1, end) ?: return null
            // iTXt：key\0 标志(1) 方法(1) 语言\0 译文\0 文本
            "iTXt" -> iTXtBody(bytes, keyEnd, end) ?: return null
            else -> bytes.copyOfRange(keyEnd, end)
        }
        return runCatching {
            if (body.size > MAX_CARD_BYTES) error("card too large")
            String(Base64.getMimeDecoder().decode(String(body, Charsets.UTF_8)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun iTXtBody(bytes: ByteArray, from: Int, end: Int): ByteArray? {
        if (from + 2 > end) return null
        val compressed = bytes[from].toInt() == 1
        val afterLang = skipField(bytes, from + 2, end) ?: return null
        val textStart = skipField(bytes, afterLang, end) ?: return null
        return if (compressed) inflate(bytes, textStart, end) else bytes.copyOfRange(textStart, end)
    }

    private fun inflate(bytes: ByteArray, from: Int, end: Int): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(bytes, from, end - from)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n <= 0) break
            out.write(buffer, 0, n)
            // 解压炸弹防护：几百字节的 zTXt 可解出数 GB，超限直接判无效
            if (out.size() > MAX_CARD_BYTES) throw IllegalStateException("card too large")
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()

    /** 找到下一个 0 字节后的位置；没有则返回 null。 */
    private fun skipField(bytes: ByteArray, from: Int, end: Int): Int? {
        for (i in from until end) if (bytes[i] == 0.toByte()) return i + 1
        return null
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val KEYS = setOf("chara", "ccv3")
}

/** 角色卡解压/解码总量上限：导入一张异常卡最多处理 4MB，防止 OOM。 */
private const val MAX_CARD_BYTES = 4_000_000
