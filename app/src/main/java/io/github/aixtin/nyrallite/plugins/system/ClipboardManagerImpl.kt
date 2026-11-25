package io.github.aixtin.nyrallite.plugins.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.aixtin.nyrallite.contract.ClipboardContract

/** 基于系统 ClipboardManager 的剪贴板实现。 */
class ClipboardManagerImpl(private val context: Context) : ClipboardContract {

    private val clipboard: ClipboardManager? =
        context.getSystemService(ClipboardManager::class.java)

    override fun readText(): String? = runCatching {
        val clip = clipboard?.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        clip.getItemAt(0).text?.toString()
    }.getOrNull()

    override fun writeText(text: String): Boolean = runCatching {
        val manager = clipboard ?: return@runCatching false
        manager.setPrimaryClip(ClipData.newPlainText("Nyral", text))
        true
    }.getOrDefault(false)
}
