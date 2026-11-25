package io.github.aixtin.nyrallite.plugins.memorystore

/**
 * 抽取去重：模型可能把同一件事换个说法再记一遍，落库前用字面相似度兜一层。
 * 提示词里已经给出已有正文，这里只是确定性兜底；跨语言改写仍交给模型判断。
 */
internal object MemoryDedup {

    /** 相同词汇占比达到此值即视为重复。 */
    private const val MAX_OVERLAP = 0.6

    /** 过滤掉与 known 或同批候选重复的条目，保留 (标题, 正文)。 */
    fun filter(known: List<String>, candidates: List<Pair<String, String>>): List<Pair<String, String>> {
        val kept = mutableListOf<String>()
        return candidates.filter { (_, text) ->
            val dup = (known + kept).any { similar(text, it) }
            if (!dup) kept += text
            !dup
        }
    }

    private fun similar(a: String, b: String): Boolean {
        val ca = compact(a)
        val cb = compact(b)
        // 去掉空白标点后互相包含，基本就是复述同一句话
        if (ca.length >= 4 && cb.length >= 4 && (ca.contains(cb) || cb.contains(ca))) return true
        val ga = MemoryScoring.grams(a)
        val gb = MemoryScoring.grams(b)
        if (ga.isEmpty() || gb.isEmpty()) return false
        val shared = ga.count { it in gb }
        return shared.toDouble() / minOf(ga.size, gb.size) >= MAX_OVERLAP
    }

    private fun compact(text: String): String =
        text.lowercase().replace(WHITESPACE_OR_PUNCT, "")

    private val WHITESPACE_OR_PUNCT =
        Regex("[\\s\\p{Punct}，。、；：？！“”‘’（）【】《》…—－·]")
}
