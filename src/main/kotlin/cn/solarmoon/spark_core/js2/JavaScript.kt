package cn.solarmoon.spark_core.js2

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import java.nio.charset.StandardCharsets

class JavaScript(
    val index: ResourceLocation,
    val content: ByteArray
) {

    val stringContent = content.toString(StandardCharsets.UTF_8)

    internal var value: Value? = null

    fun getValueOrThrow() = value ?: throw NullPointerException("脚本 $index 尚未被解析，解析值不可用")

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, JavaScript::index,
            ByteBufCodecs.BYTE_ARRAY, JavaScript::content,
            ::JavaScript
        )
    }

}