package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.anim.AnimationSet
import cn.solarmoon.spark_core.animation.model.CommonModel
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
data class AnimData(
    var modelPath: ResourceLocation,
    var animPath: ResourceLocation,
    var modelType: ModelType,
    val playData: AnimPlayData,
) {
    constructor(res: ResourceLocation, modelType: ModelType, playData: AnimPlayData): this(res, res, modelType, playData)

    val model get() = CommonModel.get(modelPath)
    val animationSet get() = AnimationSet.get(animPath)
    val textureLocation get() = ResourceLocation.fromNamespaceAndPath(modelPath.namespace, "textures/${modelType.id}/${modelPath.path}.png")

    fun copy(): AnimData {
        return AnimData(modelPath, animPath, modelType, playData.copy())
    }

    /**
     * 一键将模型和动画变为指定路径的模型和动画
     */
    fun changeTo(path: ResourceLocation, modelType: ModelType) {
        modelPath = path
        animPath = path
        this.modelType = modelType
    }

    companion object {
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, AnimData> {
            override fun decode(buffer: RegistryFriendlyByteBuf): AnimData {
                val model = buffer.readResourceLocation()
                val animations = buffer.readResourceLocation()
                val type = ModelType.STREAM_CODEC.decode(buffer)
                val play = AnimPlayData.STREAM_CODEC.decode(buffer)
                return AnimData(model, animations, type, play)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: AnimData) {
                buffer.writeResourceLocation(value.modelPath)
                buffer.writeResourceLocation(value.animPath)
                ModelType.STREAM_CODEC.encode(buffer, value.modelType)
                AnimPlayData.STREAM_CODEC.encode(buffer, value.playData)
            }
        }

        @JvmStatic
        val CODEC: Codec<AnimData> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter { it.modelPath },
                ResourceLocation.CODEC.fieldOf("anim_path").forGetter { it.animPath },
                ModelType.CODEC.fieldOf("model_type").forGetter { it.modelType },
                AnimPlayData.CODEC.fieldOf("play").forGetter { it.playData },
            ).apply(it, ::AnimData)
        }

        @JvmStatic
        val EMPTY get() = AnimData(ResourceLocation.withDefaultNamespace("player"), ResourceLocation.withDefaultNamespace("player"), ModelType.ENTITY, AnimPlayData.EMPTY)

        @JvmStatic
        fun of(entity: Entity) = AnimData(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type), ModelType.ENTITY, AnimPlayData.EMPTY)
    }

}