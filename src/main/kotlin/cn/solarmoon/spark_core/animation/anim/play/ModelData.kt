package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
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
data class ModelData(
    var modelPath: ResourceLocation,
    var animPath: ResourceLocation,
    var modelType: ModelType,
) {
    constructor(res: ResourceLocation, modelType: ModelType): this(res, res, modelType)

    val model get() = OModel.get(modelPath)
    val animationSet get() = OAnimationSet.get(animPath)
    val textureLocation get() = ResourceLocation.fromNamespaceAndPath(modelPath.namespace, "textures/${modelType.id}/${modelPath.path}.png")

    val bones = hashMapOf<String, Bone>()

    fun getBone(name: String, animatable: IAnimatable<*>) = bones.getOrPut(name) { Bone(animatable, name) }

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
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, ModelData> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ModelData {
                val model = buffer.readResourceLocation()
                val animations = buffer.readResourceLocation()
                val type = ModelType.STREAM_CODEC.decode(buffer)
                return ModelData(model, animations, type)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ModelData) {
                buffer.writeResourceLocation(value.modelPath)
                buffer.writeResourceLocation(value.animPath)
                ModelType.STREAM_CODEC.encode(buffer, value.modelType)
            }
        }

        @JvmStatic
        val CODEC: Codec<ModelData> = RecordCodecBuilder.create {
            it.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter { it.modelPath },
                ResourceLocation.CODEC.fieldOf("anim_path").forGetter { it.animPath },
                ModelType.CODEC.fieldOf("model_type").forGetter { it.modelType }
            ).apply(it, ::ModelData)
        }

        @JvmStatic
        val EMPTY get() = ModelData(ResourceLocation.withDefaultNamespace("player"), ResourceLocation.withDefaultNamespace("player"), ModelTypes.ENTITY)

        @JvmStatic
        fun of(entity: Entity) = ModelData(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type), ModelTypes.ENTITY)
    }

}