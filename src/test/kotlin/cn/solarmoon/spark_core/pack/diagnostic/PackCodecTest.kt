package cn.solarmoon.spark_core.pack.diagnostic

import com.mojang.serialization.Codec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackCodecTest {

    @Test
    fun `missing field becomes a content exception`() {
        val codec = Codec.INT.fieldOf("mass").codec()
        val error = assertThrows(PackContentException::class.java) {
            PackCodec.read(codec, "{}".toByteArray())
        }
        assertEquals("Missing required field: mass", error.userMessage.string)
    }

    @Test
    fun `json syntax error carries position`() {
        val error = assertThrows(PackContentException::class.java) {
            PackCodec.read(Codec.STRING, "{\"a\": }".toByteArray())
        }
        assertTrue(error.userMessage.string.startsWith("JSON syntax error: "), error.userMessage.string)
        assertTrue(error.userMessage.string.contains("line"), error.userMessage.string)
    }

    @Test
    fun `type mismatch is a content exception`() {
        val error = assertThrows(PackContentException::class.java) {
            PackCodec.read(Codec.INT, "\"abc\"".toByteArray())
        }
        assertTrue(error.userMessage.string.contains("number"), error.userMessage.string)
    }

    @Test
    fun `valid json decodes`() {
        assertEquals("hello", PackCodec.read(Codec.STRING, "\"hello\"".toByteArray()))
    }

    @Test
    fun `missing field text keeps only the field name`() {
        assertEquals(
            "Missing required field: mass",
            DfuText.toComponent("""No key mass in {"mass":1,"other":2}""").string,
        )
    }

    @Test
    fun `long messages are truncated`() {
        assertEquals(300, DfuText.toComponent("x".repeat(500)).string.length)
    }
}
