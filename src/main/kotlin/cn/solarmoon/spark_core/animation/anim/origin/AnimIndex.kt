package cn.solarmoon.spark_core.animation.anim.origin

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

data class AnimIndex(
    val index: ResourceLocation,
    val name: String
) {

    companion object {
        val CODEC: Codec<AnimIndex> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("index").forGetter { it.index },
                Codec.STRING.fieldOf("name").forGetter { it.name }
            ).apply(it, ::AnimIndex)
        }
    }

    val locationName get() = "$index/${name.replace(regex = Regex("[^a-z0-9/._-]"), "_")}"

    override fun toString(): String {
        return "$index/$name"
    }

}