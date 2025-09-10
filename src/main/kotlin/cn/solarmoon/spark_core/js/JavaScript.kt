package cn.solarmoon.spark_core.js

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets

class JavaScript(
    val index: ResourceLocation,
    val content: ByteArray
) {

    val stringContent = content.toString(StandardCharsets.UTF_8)

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, JavaScript::index,
            ByteBufCodecs.BYTE_ARRAY, JavaScript::content,
            ::JavaScript
        )
    }

}