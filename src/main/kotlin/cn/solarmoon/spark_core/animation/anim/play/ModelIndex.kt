package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

/**
 * 保存了客户端渲染完整动画和模型所需的必要数据
 *
 * 模型和动画路径格式如：minecraft:player 一般和该实体注册id一致
 */
class ModelIndex (
    modelPath: ResourceLocation,
    animPath: ResourceLocation,
    textureLocation: ResourceLocation
) {
    constructor(registryKey: ResourceLocation, textureLocation: ResourceLocation): this(registryKey, registryKey, textureLocation)

    var modelPath = modelPath
    var animPath = animPath
    var textureLocation = textureLocation

    val model get() = OModel.get(modelPath)
    val animationSet get() = OAnimationSet.get(animPath)

    companion object {
        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModelIndex::modelPath,
            ResourceLocation.STREAM_CODEC, ModelIndex::animPath,
            ResourceLocation.STREAM_CODEC, ModelIndex::textureLocation,
            ::ModelIndex
        )

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter { it.modelPath },
                ResourceLocation.CODEC.fieldOf("anim_path").forGetter { it.animPath },
                ResourceLocation.CODEC.fieldOf("texture").forGetter { it.textureLocation }
            ).apply(it, ::ModelIndex)
        }

        @JvmStatic
        val EMPTY get() = ModelIndex(ResourceLocation.withDefaultNamespace("player"), ResourceLocation.withDefaultNamespace("player"), ResourceLocation.withDefaultNamespace("player"))

        @JvmStatic
        fun of(entity: Entity): ModelIndex {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
            return ModelIndex(key, ResourceLocation.fromNamespaceAndPath(key.namespace, "textures/entity/${key.path}.png"))
        }
    }

}