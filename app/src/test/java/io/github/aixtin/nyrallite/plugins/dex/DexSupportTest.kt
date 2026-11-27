package io.github.aixtin.nyrallite.plugins.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 插件入口解析的回归测试：入口声明是分发层的信任边界输入，改解析逻辑前先看这里。 */
class DexSupportTest {

    private fun apk(vararg entries: Pair<String, String>): File {
        val f = File.createTempFile("plugin", ".apk")
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun readsAssetsEntry() {
        val f = apk("assets/plugin-entry.txt" to "Plugin-Entry: com.example.MyPlugin\n")
        assertEquals("com.example.MyPlugin", DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun assetsEntryWinsOverLegacy() {
        val f = apk(
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\nPlugin-Entry: legacy.Entry\n",
            "assets/plugin-entry.txt" to "Plugin-Entry: assets.Entry\n",
        )
        assertEquals("assets.Entry", DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun legacyManifestFallback() {
        val f = apk("META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\nPlugin-Entry: legacy.Entry\n")
        assertEquals("legacy.Entry", DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun entryFileNameIsCaseInsensitive() {
        val f = apk("assets/Plugin-Entry.txt" to "Plugin-Entry: com.example.Caps\n")
        assertEquals("com.example.Caps", DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun trimsWhitespaceAndSkipsOtherLines() {
        val f = apk("assets/plugin-entry.txt" to "other line\nPlugin-Entry:    com.example.Spaced  \n\n")
        assertEquals("com.example.Spaced", DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun blankEntryIsNull() {
        val f = apk("assets/plugin-entry.txt" to "Plugin-Entry:   \n")
        assertNull(DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun missingEntryIsNull() {
        val f = apk("assets/other.txt" to "hello\n")
        assertNull(DexSupport.readPluginEntry(f))
        f.delete()
    }

    @Test
    fun unreadableFileIsNull() {
        assertNull(DexSupport.readPluginEntry(File("/nonexistent/plugin.apk")))
    }
}
