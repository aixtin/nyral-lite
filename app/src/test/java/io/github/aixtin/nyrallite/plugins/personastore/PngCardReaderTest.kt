package io.github.aixtin.nyrallite.plugins.personastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class PngCardReaderTest {

    @Test
    fun readsCardFromTextChunk() {
        val json = """{"spec":"chara_card_v2","data":{"name":"Fuka"}}"""
        val png = png(textChunk("chara", json))

        assertTrue(PngCardReader.isPng(png))
        assertEquals(json, PngCardReader.jsonText(png))
    }

    @Test
    fun prefersLastCardChunk() {
        val first = """{"spec":"chara_card_v2","data":{"name":"旧"}}"""
        val second = """{"spec":"chara_card_v2","data":{"name":"新"}}"""
        val png = png(textChunk("chara", first), textChunk("chara", second))

        assertEquals(second, PngCardReader.jsonText(png))
    }

    @Test
    fun ignoresUnrelatedChunksAndNonPng() {
        val png = png(textChunk("Software", """{"a":1}"""))

        assertNull(PngCardReader.jsonText(png))
        assertNull(PngCardReader.jsonText("普通文本".toByteArray()))
    }

    private fun textChunk(key: String, value: String): Pair<String, ByteArray> =
        "tEXt" to (
            key.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
                Base64.getEncoder().encode(value.toByteArray(Charsets.UTF_8))
            )

    /** 只拼出读取器需要的骨架：头 + 文本块 + IEND。 */
    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunks.forEach { (type, data) ->
            out.write(intBytes(data.size))
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(data)
            out.write(byteArrayOf(0, 0, 0, 0))
        }
        out.write(intBytes(0))
        out.write("IEND".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 0))
        return out.toByteArray()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private companion object {
        val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
