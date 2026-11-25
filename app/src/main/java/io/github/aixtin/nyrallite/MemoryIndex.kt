package io.github.aixtin.nyrallite

import io.github.aixtin.nyrallite.contract.MemoryEntry
import io.github.aixtin.nyrallite.contract.MemoryStore
import io.github.aixtin.nyrallite.kernel.getOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆目录：每轮只注入「编号 + 时间 + 标题」，正文交给 memory_search 按需取。
 *
 * 目录长度只与记忆条数有关，不随会话变长而膨胀——这是它比全量注入摘要可靠的地方。
 */
internal object MemoryIndex {

    /** 只放置顶与最近的条目；更早的仍可被检索到，只是不进目录。 */
    private const val LIMIT = 20

    fun blockFor(app: App): String {
        val store = app.kernel.services.getOrNull<MemoryStore>() ?: return ""
        return runCatching { block(store) }.getOrDefault("")
    }

    fun block(store: MemoryStore): String {
        val rows = store.all().sortedWith(
            compareByDescending<MemoryEntry> { it.pinned }
                .thenByDescending { it.ts },
        ).take(LIMIT)
        if (rows.isEmpty()) return ""
        val dates = SimpleDateFormat("MM-dd", Locale.getDefault())
        return buildString {
            append("\n\n[记忆索引]\n")
            rows.forEach { row ->
                append('#').append(row.id).append(' ')
                    .append(dates.format(Date(row.ts))).append(' ')
                    .append(row.title).append('\n')
            }
            append("(以上是长期记忆目录；需要具体细节时调用 memory_search 按主题检索，不要凭标题臆测内容)\n")
        }
    }
}
