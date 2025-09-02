package cn.solarmoon.spark_core.animation.anim.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class AnimIndex(
    val modelPath: ResourceLocation,
    val name: String
) {

    companion object {
        val CODEC: Codec<AnimIndex> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("inputPath").forGetter { it.modelPath },
                Codec.STRING.fieldOf("name").forGetter { it.name },
            ).apply(it, ::AnimIndex)
        }

        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, AnimIndex::modelPath,
            ByteBufCodecs.STRING_UTF8, AnimIndex::name,
            ::AnimIndex
        )
    }

    override fun toString(): String {
        return "$modelPath/$name"
    }

}