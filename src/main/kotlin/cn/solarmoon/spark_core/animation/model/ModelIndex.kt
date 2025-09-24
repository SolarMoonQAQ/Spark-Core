package cn.solarmoon.spark_core.animation.model

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

/**
 * 保存了客户端渲染完整模型所需的必要数据
 */
class ModelIndex (
    val location: ResourceLocation
) {

    fun isPlayer(): Boolean {
        return location == ResourceLocation.withDefaultNamespace("player")
    }

    companion object {
        // 定义一个默认的空 IK 路径，或者让 null 代表无 IK
        val EMPTY_IK_PATH: ResourceLocation? = null // 或者 ResourceLocation("spark", "empty_ik")

        // 更新STREAM_CODEC以包含ikPath，使用ByteBufCodecs.optional处理可空的ResourceLocation
        @JvmStatic
        val STREAM_CODEC: StreamCodec<in RegistryFriendlyByteBuf, ModelIndex> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModelIndex::location
        ) { model -> ModelIndex(model) }

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter(ModelIndex::location),
            ).apply(instance) { model ->
                ModelIndex(model) // 如果字段不存在，则 ikPath 为 null
            }
        }


        @JvmStatic
        val EMPTY get() = ModelIndex(
            ResourceLocation.fromNamespaceAndPath("minecraft", "empty"),
        )

        @JvmStatic
        fun of(type: EntityType<*>): ModelIndex {
            val id = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            val modelPath = id
            return ModelIndex(modelPath)
        }

        @JvmStatic
        fun of(item: Item): ModelIndex {
            val id = BuiltInRegistries.ITEM.getKey(item)
            val modelPath = id
            return ModelIndex(modelPath)
        }
    }
}