package io.github.aixtin.nyrallite.stressplugin

/** 压力工具统一接口：装载后跨类分发调用。 */
interface StressOps {
    fun transform(input: String): String
    fun fold(values: List<Int>): Long
    fun bucket(items: List<String>): Map<Int, List<String>>
    fun matrix(n: Int): Long
}
