package cn.solarmoon.spark_core.animation.model

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * 保存了客户端渲染完整模型所需的必要数据
 *
 * @param fullDetailDistance 全细节距离（米），物体在此距离以内以 LOD 0（全细节）渲染。
 *                           默认 24m 适合多数中型模型，大物体设大值（如车体 24m），小物体设小值（如螺栓 4m）。
 */
data class ModelIndex @JvmOverloads constructor(
    val type: String,
    val location: ResourceLocation,
    val fullDetailDistance: Double = 24.0
) {

    fun isPlayer(): Boolean {
        return type == "entity" && location == ResourceLocation.withDefaultNamespace("player")
    }

    override fun toString(): String {
        return "[$type]$location"
    }

    companion object {
        @JvmStatic
        val STREAM_CODEC: StreamCodec<in RegistryFriendlyByteBuf, ModelIndex> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModelIndex::type,
            ResourceLocation.STREAM_CODEC, ModelIndex::location,
            ByteBufCodecs.DOUBLE, ModelIndex::fullDetailDistance,
            ::ModelIndex
        )

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("type").forGetter { it.type },
                ResourceLocation.CODEC.fieldOf("path").forGetter(ModelIndex::location),
                Codec.DOUBLE.optionalFieldOf("full_detail_distance", 24.0).forGetter { it.fullDetailDistance },
            ).apply(instance, ::ModelIndex)
        }

        @JvmStatic
        val EMPTY get() = ModelIndex("null", ResourceLocation.fromNamespaceAndPath("minecraft", "empty"))

        @JvmStatic
        fun of(type: EntityType<*>): ModelIndex {
            val id = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            val modelPath = id
            return ModelIndex("entity", modelPath)
        }

        @JvmStatic
        fun of(type: BlockEntityType<*>): ModelIndex {
            val id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)!!
            val modelPath = id
            return ModelIndex("block", modelPath)
        }

        @JvmStatic
        fun of(item: Item): ModelIndex {
            val id = BuiltInRegistries.ITEM.getKey(item)
            val modelPath = id
            return ModelIndex("item", modelPath)
        }
    }
}