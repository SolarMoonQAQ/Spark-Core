package cn.solarmoon.spark_core.pack.modules

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LangModuleTest {

    private fun location(namespace: String, code: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(namespace, "lang/$code.json")

    private fun json(vararg pairs: Pair<String, String>): List<ByteArray> =
        listOf(
            pairs.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                """"$key":"$value""""
            }
        ).map { it.toByteArray() }

    private fun parse(bytes: ByteArray): JsonObject =
        JsonParser.parseString(bytes.decodeToString()).asJsonObject

    /** 回归：不同命名空间提供同名语言文件时必须全部写出，不能被 langCode 全局键顶掉 */
    @Test
    fun `不同命名空间的同语言文件都会写出`() {
        val collected = mapOf(
            location("moji", "zh_cn") to json("moji.a" to "甲"),
            location("moji", "en_us") to json("moji.a" to "A"),
            location("t90a", "zh_cn") to json("t90a.b" to "乙"),
            location("t90a", "en_us") to json("t90a.b" to "B"),
            location("m1a2_sep", "zh_cn") to json("m1a2_sep.c" to "丙"),
            location("m1a2_sep", "en_us") to json("m1a2_sep.c" to "C"),
        )

        val files = LangModule.buildLangFiles(collected)

        assertEquals(6, files.size)
        assertEquals("甲", parse(files.getValue(location("moji", "zh_cn"))).get("moji.a").asString)
        assertEquals("乙", parse(files.getValue(location("t90a", "zh_cn"))).get("t90a.b").asString)
        assertEquals("丙", parse(files.getValue(location("m1a2_sep", "zh_cn"))).get("m1a2_sep.c").asString)
    }

    /** 回退只能使用同命名空间的 zh_tw / zh_hk / en_us，不能串到别的命名空间 */
    @Test
    fun `中文回退只在同命名空间内进行`() {
        val collected = mapOf(
            location("moji", "zh_cn") to json("moji.a" to "甲"),
            location("moji", "en_us") to json("moji.a" to "A", "moji.only_en" to "MojiEn"),
            location("t90a", "zh_cn") to json("t90a.b" to "乙"),
            location("t90a", "en_us") to json("t90a.b" to "B", "t90a.only_en" to "T90En"),
        )

        val files = LangModule.buildLangFiles(collected)

        val mojiZh = parse(files.getValue(location("moji", "zh_cn")))
        assertEquals("甲", mojiZh.get("moji.a").asString)
        assertEquals("MojiEn", mojiZh.get("moji.only_en").asString)
        assertNull(mojiZh.get("t90a.only_en"))

        val t90aZh = parse(files.getValue(location("t90a", "zh_cn")))
        assertEquals("乙", t90aZh.get("t90a.b").asString)
        assertEquals("T90En", t90aZh.get("t90a.only_en").asString)
        assertNull(t90aZh.get("moji.only_en"))
    }

    /** 同命名空间的多包贡献仍按读取顺序按键合并，后读到的覆盖先读到的 */
    @Test
    fun `同命名空间的多包贡献按读取顺序合并`() {
        val lang = location("machine_max", "zh_cn")
        val collected = mapOf(
            lang to listOf(
                """{"machine_max.a":"基础","machine_max.b":"旧"}""".toByteArray(),
                """{"machine_max.b":"新"}""".toByteArray(),
            )
        )

        val merged = LangModule.mergeCollectedContents(collected)

        assertEquals(1, merged.size)
        val table = merged.getValue(lang)
        assertEquals("基础", table["machine_max.a"])
        assertEquals("新", table["machine_max.b"])
    }
}
