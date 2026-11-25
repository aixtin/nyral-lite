package io.github.aixtin.nyrallite.plugins.memorystore

import io.github.aixtin.nyrallite.contract.MemoryEntry

/**
 * 记忆检索打分：不引向量模型，用「中文二元组 + 英文词」命中数 + 时间衰减。
 * 条目在几百条量级时足够区分主题；上千条再考虑换 embedding。
 */
internal object MemoryScoring {

    /** 低于此分视为无关，宁可不给模型，也别塞进去干扰。 */
    const val MIN_SCORE = 5.0

    private const val MAX_GRAMS = 24
    private const val DECAY_PER_DAY = 1.0 / 30.0
    private const val MAX_DECAY = 5.0
    private const val EXACT_BONUS = 20.0
    private const val PINNED_BONUS = 3.0

    /**
     * 切分查询：连续 ASCII 字母数字算一个词，中日韩字符按两字滑窗。
     * 中文单字太泛（"的""我"），二元组才有区分度。
     */
    fun grams(query: String): Set<String> {
        val text = query.lowercase()
        val out = LinkedHashSet<String>()
        val ascii = StringBuilder()
        fun flushAscii() {
            if (ascii.length >= 2) out += ascii.toString()
            ascii.clear()
        }
        var i = 0
        while (i < text.length && out.size < MAX_GRAMS) {
            val c = text[i]
            if (c.code <= 0x7f) {
                if (c.isLetterOrDigit() || c == '_' || c == '-') {
                    ascii.append(c)
                } else {
                    flushAscii()
                }
                i++
                continue
            }
            flushAscii()
            if (isCjk(c) && i + 1 < text.length && isCjk(text[i + 1])) {
                out += text.substring(i, i + 2)
            }
            i++
        }
        flushAscii()
        return out
    }

    fun score(entry: MemoryEntry, query: String, grams: Set<String>, now: Long): Double {
        if (grams.isEmpty()) return 0.0
        val haystack = (entry.title + "\n" + entry.text).lowercase()
        var hits = 0
        for (gram in grams) if (haystack.contains(gram)) hits++
        var score = hits * 10.0
        val normalized = query.trim().lowercase()
        if (normalized.length >= 2 && haystack.contains(normalized)) score += EXACT_BONUS
        if (entry.pinned) score += PINNED_BONUS
        val days = (now - entry.ts).coerceAtLeast(0L) / 86_400_000.0
        return score - (days * DECAY_PER_DAY).coerceAtMost(MAX_DECAY)
    }

    /** 写入时没给标题：取首句压成一行，别让索引页出现多行。 */
    fun titleOf(text: String): String = text.trim()
        .replace(Regex("\\s+"), " ")
        .take(24)
        .ifBlank { "未命名记忆" }

    fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF
}
