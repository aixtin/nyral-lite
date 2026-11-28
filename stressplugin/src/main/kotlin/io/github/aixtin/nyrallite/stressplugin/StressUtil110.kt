package io.github.aixtin.nyrallite.stressplugin

/** 压力测试类 110：字符串与集合处理逻辑。 */
class StressUtil110 : StressOps {

    private val seed = 110 * 31L

    override fun transform(input: String): String {
        val sb = StringBuilder(input.length + 16)
        for ((i, ch) in input.withIndex()) {
            when {
                ch.isLetterOrDigit() -> sb.append(ch.uppercaseChar())
                else -> sb.append('_')
            }
            if (i % 7 == 6) sb.append('|').append((seed % 97).toInt())
        }
        return sb.toString()
    }

    override fun fold(values: List<Int>): Long {
        var acc = seed
        for (v in values) {
            acc = acc * 31 + v
            if (acc > 1_000_000_000) acc %= 1_000_000_007
        }
        return acc
    }

    override fun bucket(items: List<String>): Map<Int, List<String>> =
        items.groupBy { it.length * 110 % 11 }

    override fun matrix(n: Int): Long {
        var trace = 0L
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) trace += (i * 110 + j).toLong()
            }
        }
        return trace
    }
}
