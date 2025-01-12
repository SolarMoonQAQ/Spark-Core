package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

data class OLocator(
    val offset: Vec3,
    val rotation: Vec3
) {

    companion object {
        @JvmStatic
        val CODEC: Codec<OLocator> = Codec.either(
            Vec3.CODEC,
            RecordCodecBuilder.create { instance ->
                instance.group(
                    Vec3.CODEC.fieldOf("offset").forGetter(OLocator::offset),
                    Vec3.CODEC.fieldOf("rotation").forGetter(OLocator::rotation)
                ).apply(instance, ::OLocator)
            }
        ).xmap(
            { it.map({ OLocator(it, Vec3.ZERO) }, { it }) },
            { if (it.rotation == Vec3.ZERO) Either.left(it.offset) else Either.right(it) }
        )

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC).xmap({ LinkedHashMap(it) }, { it })

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SerializeHelper.VEC3_STREAM_CODEC, OLocator::offset,
            SerializeHelper.VEC3_STREAM_CODEC, OLocator::rotation,
            ::OLocator
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, STREAM_CODEC)
    }

}