package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.model.ModelIndex
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class AnimIndex(
    val modelIndex: ModelIndex,
    val name: String
) {

    companion object {
        val CODEC: Codec<AnimIndex> = RecordCodecBuilder.create {
            it.group(
                ModelIndex.CODEC.fieldOf("model_index").forGetter { it.modelIndex },
                Codec.STRING.fieldOf("name").forGetter { it.name },
            ).apply(it, ::AnimIndex)
        }

        val STREAM_CODEC = StreamCodec.composite(
            ModelIndex.STREAM_CODEC, AnimIndex::modelIndex,
            ByteBufCodecs.STRING_UTF8, AnimIndex::name,
            ::AnimIndex
        )
    }

    override fun toString(): String {
        return "$modelIndex/$name"
    }

}