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
 */
data class ModelIndex (
    val type: String,
    val location: ResourceLocation
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
            ::ModelIndex
        )

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("type").forGetter { it.type },
                ResourceLocation.CODEC.fieldOf("path").forGetter(ModelIndex::location),
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