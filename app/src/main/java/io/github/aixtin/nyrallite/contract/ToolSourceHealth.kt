package io.github.aixtin.nyrallite.contract

/**
 * 工具来源健康状况：聚合注册表把各来源的失败原因收集起来，
 * 供界面在不打断其它工具的前提下给出提示。
 */
interface ToolSourceHealth {

    /** 最近一次 list() 里失败的来源原因；全部成功时为空。 */
    fun lastErrors(): List<String>
}
