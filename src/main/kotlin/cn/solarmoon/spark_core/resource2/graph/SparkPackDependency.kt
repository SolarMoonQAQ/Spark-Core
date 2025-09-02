package cn.solarmoon.spark_core.resource2.graph

import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

class SparkPackDependency(
    val id: ResourceLocation,
    val type: DependencyType
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(SparkPackDependency::id),
                DependencyType.CODEC.fieldOf("type").forGetter(SparkPackDependency::type)
            ).apply(it, ::SparkPackDependency)
        }

        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SparkPackDependency::id,
            DependencyType.STREAM_CODEC, SparkPackDependency::type,
            ::SparkPackDependency
        )
    }

}