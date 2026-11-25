package io.github.aixtin.nyrallite.contract

/**
 * Skill 文档扫描器：从 files/skills 和 files/user_skills 目录里读取 SKILL.md，
 * 解析 Frontmatter（name / description），匹配用户当前上下文，返回匹配到的指令块。
 *
 * 这是"参考 Web 插件思路"的首版实现——纯 Markdown，零代码加载，零安全风险。
 * 类似 Trae Skill 的高层建模，但只做"指令注入"这一件事。
 */
interface SkillScanner {

    /**
     * 扫描并匹配当前用户消息。
     *
     * @param userMessage 本轮用户输入，用于和每个 Skill 的 description 做关键词匹配
     * @return 匹配到的 Skill 指令块（按匹配度排序）；返回空串表示没有匹配
     */
    suspend fun match(userMessage: String): String

    /** 扫描所有 Skill 的元信息，供 UI 展示。 */
    suspend fun list(): List<SkillDoc>
}

data class SkillDoc(
    val name: String,
    val description: String,
    val path: String, // 文件路径，用于"禁用/启用"等管理操作
    val isBuiltin: Boolean, // true = files/skills（随 APK）；false = files/user_skills（用户导入）
    val enabled: Boolean, // 用户是否启用（持久化到 SharedPrefs）
)
