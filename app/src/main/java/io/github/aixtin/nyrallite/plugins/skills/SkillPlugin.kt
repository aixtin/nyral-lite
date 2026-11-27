package io.github.aixtin.nyrallite.plugins.skills

import android.content.Context
import io.github.aixtin.nyrallite.contract.SkillDoc
import io.github.aixtin.nyrallite.contract.SkillScanner
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.provide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Skill 插件：扫描 files/skills 和 files/user_skills 里的 SKILL.md，
 * 解析 Frontmatter（YAML 格式），在每次 ChatRequest 发送前匹配用户消息，
 * 把匹配到的 Skill 指令块拼进 system prompt。
 *
 * SKILL.md 格式：
 * ```
 * ---
 * name: code-review
 * description: 执行代码审查，检查质量和安全
 * ---
 *
 * # code-review 指令
 * ...
 * ```
 *
 * 匹配策略（首版简单关键词包含）：用户消息里包含 description 的任意 3+ 字片段 = 命中。
 * 以后可以升级成 embedding 语义匹配。
 */
class SkillPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "skills",
        permissions = setOf(Permission.FILES),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        val scanner = FileSkillScanner(ctx.appContext)
        ctx.services.provide(SkillScanner::class, scanner, requires = setOf(Permission.FILES))
    }
}

class FileSkillScanner(
    private val appContext: Context,
) : SkillScanner {

    override suspend fun match(userMessage: String): String = withContext(Dispatchers.IO) {
        val docs = loadAll()
        if (docs.isEmpty()) return@withContext ""
        val lower = userMessage.lowercase()
        val matched = docs.filter { doc ->
            doc.enabled && isRelevant(doc.description.lowercase(), lower)
        }
        if (matched.isEmpty()) return@withContext ""

        var total = 0
        val blocks = matched.mapNotNull { doc ->
            if (total >= MAX_TOTAL_CHARS) return@mapNotNull null
            val body = stripFrontmatter(readContent(doc.path)).take(MAX_SKILL_CHARS)
            total += body.length
            "## 已加载 Skill：${doc.name}\n$body"
        }
        if (blocks.isEmpty()) return@withContext ""
        "\n\n## 技能\n\n${blocks.joinToString("\n\n")}"
    }

    override suspend fun list(): List<SkillDoc> = withContext(Dispatchers.IO) {
        loadAll()
    }

    private fun loadAll(): List<SkillDoc> {
        val builtinDir = File(appContext.filesDir, "skills")
        val userDir = File(appContext.filesDir, "user_skills")
        val all = ArrayList<SkillDoc>()
        collectFrom(builtinDir, isBuiltin = true, all)
        collectFrom(userDir, isBuiltin = false, all)
        return all
    }

    private fun collectFrom(dir: File, isBuiltin: Boolean, out: MutableList<SkillDoc>) {
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { sub ->
            val skillMd = File(sub, "SKILL.md")
            if (skillMd.isFile) {
                val content = skillMd.readText()
                val meta = parseFrontmatter(content)
                out += SkillDoc(
                    name = meta["name"] ?: sub.name,
                    description = meta["description"] ?: "",
                    path = skillMd.absolutePath,
                    isBuiltin = isBuiltin,
                    enabled = isEnabled(skillMd.absolutePath),
                )
            }
        }
    }

    /** 首版简单匹配：用户消息里包含 description 的 3+ 字连续片段 = 命中。 */
    private fun isRelevant(descLower: String, userLower: String): Boolean {
        if (descLower.isBlank()) return false
        // 取 description 的前 20 字片段，检查是否有 3 字以上在 user 里出现
        val window = descLower.take(20)
        for (len in 6 downTo 3) {
            for (i in 0..window.length - len) {
                val sub = window.substring(i, i + len)
                if (sub in userLower) return true
            }
        }
        return false
    }

    private fun readContent(path: String): String =
        runCatching { File(path).readText() }.getOrDefault("")

    private fun stripFrontmatter(content: String): String {
        if (!content.startsWith("---")) return content.trim()
        val end = content.indexOf("\n---", 3)
        if (end < 0) return content.trim()
        return content.substring(end + 4).trim()
    }

    private fun parseFrontmatter(content: String): Map<String, String> {
        if (!content.startsWith("---")) return emptyMap()
        val end = content.indexOf("\n---", 3)
        if (end < 0) return emptyMap()
        val header = content.substring(3, end)
        val map = LinkedHashMap<String, String>()
        for (line in header.lines()) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim().trim('"', '\'')
                if (key.isNotEmpty()) map[key] = value
            }
        }
        return map
    }

    /** 从 SharedPrefs 读 Skill 启用状态；默认全部启用。 */
    private fun isEnabled(path: String): Boolean {
        val sp = appContext.getSharedPreferences("nyral_skill_enabled", Context.MODE_PRIVATE)
        return sp.getBoolean(path, true)
    }
}

/** 单个 Skill 注入上限：防止超大 SKILL.md 顶掉可用上下文。 */
private const val MAX_SKILL_CHARS = 8_000

/** 全部 Skill 注入总上限。 */
private const val MAX_TOTAL_CHARS = 32_000
