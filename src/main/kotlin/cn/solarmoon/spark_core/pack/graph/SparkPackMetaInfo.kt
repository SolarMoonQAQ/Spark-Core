package cn.solarmoon.spark_core.pack.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class SparkPackMetaInfo(
    val id: ResourceLocation,
    val version: String,
    val name: Component,
    val author: Component,
    val description: Component,
    val dependencies: List<SparkPackDependency>,
) {

    val dependencyMap = buildMap { dependencies.forEach { getOrPut(it.type) { mutableListOf<ResourceLocation>() }.add(it.id) } }.mapValues { it.value.toList() }

    companion object {
        val CODEC = RecordCodecBuilder.create<SparkPackMetaInfo> {
            it.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter { it.id },
                Codec.STRING.optionalFieldOf("version", "1.0").forGetter { it.version },
                ComponentSerialization.CODEC.optionalFieldOf("name", Component.empty()).forGetter { it.name },
                ComponentSerialization.CODEC.optionalFieldOf("author", Component.empty()).forGetter { it.author },
                ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter { it.description },
                SparkPackDependency.CODEC.listOf().optionalFieldOf("dependencies", listOf()).forGetter { it.dependencies }
            ).apply(it, ::SparkPackMetaInfo)
        }

        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SparkPackMetaInfo::id,
            ByteBufCodecs.STRING_UTF8, SparkPackMetaInfo::version,
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, SparkPackMetaInfo::name,
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, SparkPackMetaInfo::author,
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, SparkPackMetaInfo::description,
            SparkPackDependency.STREAM_CODEC.apply(ByteBufCodecs.list()), SparkPackMetaInfo::dependencies,
            ::SparkPackMetaInfo
        )
    }

}