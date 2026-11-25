package io.github.aixtin.nyrallite

import androidx.appcompat.app.AppCompatActivity
import io.github.aixtin.nyrallite.contract.ChatTurn
import io.github.aixtin.nyrallite.contract.SkillScanner
import io.github.aixtin.nyrallite.kernel.getOrNull

/**
 * 拼装总统 prompt：基础指令 + 人设 + 前情摘要 + 记忆目录 + 命中的 Skill。
 * 每一块都独立兜底，任一环节失败都不影响主对话。
 */
internal object PromptAssembler {

    suspend fun build(
        activity: AppCompatActivity,
        history: List<ChatTurn>,
        personaId: String?,
        summary: () -> String,
    ): String {
        val app = activity.application as? App ?: return PromptDefaults.SYSTEM
        val skillPrompt = runCatching { matchSkill(app, history) }.getOrDefault("")
        // 记忆目录：只有标题，正文靠 memory_search 工具按需取
        val memoryBlock = runCatching { MemoryIndex.blockFor(app) }.getOrDefault("")
        // 人设按会话隔离；每轮重新取卡片，短长档位和编辑结果下一轮即生效
        val personaBlock = runCatching { PersonaIndex.blockFor(app, personaId) }.getOrDefault("")
        // 前情摘要：只放要点，原文靠 history_search 回读
        val summaryBlock = runCatching { summary() }.getOrDefault("")
        return PromptDefaults.SYSTEM + personaBlock + summaryBlock + memoryBlock + skillPrompt
    }

    /** 从 history 里取最后一条用户消息，匹配 Skill 指令块。无匹配返回空串。 */
    private suspend fun matchSkill(app: App, history: List<ChatTurn>): String {
        val scanner = app.kernel.services.getOrNull(SkillScanner::class) ?: return ""
        val lastUserMsg = history.lastOrNull { it.role == "user" }?.content ?: return ""
        if (lastUserMsg.isBlank()) return ""
        return scanner.match(lastUserMsg)
    }
}
