package cn.solarmoon.spark_core.pack.diagnostic

import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets

/**
 * 内容包 JSON 的解析入口。
 *
 * 模块不再直接使用 `.orThrow` 或裸 [JsonParser]：语法错误与 DFU 解码错误都会转换成
 * [PackContentException]，由加载器记为一条内容错误（一条记录、一行日志、无堆栈）。
 */
object PackCodec {

    @JvmStatic
    fun parse(content: ByteArray): JsonElement = try {
        JsonParser.parseString(String(content, StandardCharsets.UTF_8))
    } catch (e: JsonParseException) {
        throw PackContentException(
            Component.translatableWithFallback(
                "spark_core.pack.error.json_syntax",
                "JSON syntax error: %s",
                e.message ?: "",
            )
        )
    }

    @JvmStatic
    @JvmOverloads
    fun <T> read(
        codec: Codec<T>,
        content: ByteArray,
        ops: DynamicOps<JsonElement> = JsonOps.INSTANCE,
    ): T = read(codec, parse(content), ops)

    @JvmStatic
    @JvmOverloads
    fun <T> read(
        codec: Codec<T>,
        json: JsonElement,
        ops: DynamicOps<JsonElement> = JsonOps.INSTANCE,
    ): T = codec.decode(ops, json)
        .getOrThrow { message -> PackContentException(DfuText.toComponent(message)) }
        .first
}
