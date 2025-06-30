package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import java.util.* // 需要导入 Optional

/**
 * 保存了客户端渲染完整动画和模型所需的必要数据
 *
 * 模型和动画路径格式如：minecraft:player 一般和该实体注册id一致
 */
class ModelIndex (
    modelPath: ResourceLocation,
    animPath: ResourceLocation,
    textureLocation: ResourceLocation,
    ikPath: ResourceLocation? // 添加 IK 路径字段，可为空
) {
    // 保留旧构造函数以兼容，将 ikPath 设为 null
    constructor(registryKey: ResourceLocation, textureLocation: ResourceLocation) : this(
        registryKey,
        registryKey,
        textureLocation,
        getDefaultIkPath(registryKey) // 尝试推断 IK 路径
    )

    // 新增构造函数，允许不指定 IK
    constructor(
        modelPath: ResourceLocation,
        animPath: ResourceLocation,
        textureLocation: ResourceLocation
    ) : this(modelPath, animPath, textureLocation, getDefaultIkPath(modelPath)) // 尝试推断 IK 路径


    var modelPath = modelPath
    var animPath = animPath
    var textureLocation = textureLocation
    var ikPath = ikPath // 新增 ikPath 属性

    val model get() = OModel.get(modelPath)
    val animationSet get() = OAnimationSet.get(animPath)

    /**
     * 获取与此模型索引关联的IK约束集合
     * 如果ikPath为null或找不到约束，则返回空集合
     */
    val ikConstraints: List<OIKConstraint>
        get() {
            if (ikPath == null) return emptyList()

            // 从OIKConstraint.ORIGINS中查找所有匹配ikPath前缀的约束
            return OIKConstraint.ORIGINS.entries
                .filter { (id, _) -> id.toString().startsWith(ikPath.toString()) }
                .map { (_, constraint) -> constraint }
        }

    override fun equals(other: Any?): Boolean {
        if (other !is ModelIndex) return false
        return other.modelPath == modelPath &&
                other.animPath == animPath &&
                other.textureLocation == textureLocation &&
                other.ikPath == ikPath // 比较 ikPath
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + modelPath.hashCode()
        result = 31 * result + animPath.hashCode()
        result = 31 * result + textureLocation.hashCode()
        result = 31 * result + (ikPath?.hashCode() ?: 0) // 计算 ikPath 的哈希码
        return result
    }

    companion object {
        // 定义一个默认的空 IK 路径，或者让 null 代表无 IK
        val EMPTY_IK_PATH: ResourceLocation? = null // 或者 ResourceLocation("spark", "empty_ik")

        // 更新STREAM_CODEC以包含ikPath，使用ByteBufCodecs.optional处理可空的ResourceLocation
        @JvmStatic
        val STREAM_CODEC: StreamCodec<in RegistryFriendlyByteBuf, ModelIndex> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModelIndex::modelPath,
            ResourceLocation.STREAM_CODEC, ModelIndex::animPath,
            ResourceLocation.STREAM_CODEC, ModelIndex::textureLocation,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), { Optional.ofNullable(it.ikPath) }
        ) { model, anim, tex, ikPathOpt -> ModelIndex(model, anim, tex, ikPathOpt.orElse(null)) }

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter(ModelIndex::modelPath),
                ResourceLocation.CODEC.fieldOf("anim_path").forGetter(ModelIndex::animPath),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(ModelIndex::textureLocation),
                ResourceLocation.CODEC.optionalFieldOf("ik_path").forGetter { mi -> Optional.ofNullable(mi.ikPath) } // 使用 optionalFieldOf 读取 ik_path
            ).apply(instance) { model, anim, tex, ikOpt ->
                ModelIndex(model, anim, tex, ikOpt.orElse(null)) // 如果字段不存在，则 ikPath 为 null
            }
        }


        @JvmStatic
        val EMPTY get() = ModelIndex(
            ResourceLocation.withDefaultNamespace("empty"),
            ResourceLocation.withDefaultNamespace("empty"),
            ResourceLocation.withDefaultNamespace("empty"),
            EMPTY_IK_PATH // 使用空 IK 路径
        )

        /**
         * 推断默认 IK 路径的辅助函数
         * 检查是否存在与给定key相关的IK约束
         */
        private fun getDefaultIkPath(key: ResourceLocation): ResourceLocation? {
            val potentialIkPath = ResourceLocation.fromNamespaceAndPath(key.namespace, "${key.path}")
            val alternativeMatches = OIKConstraint.ORIGINS.keys.filter {
                it.toString().startsWith((potentialIkPath.toString()))
            }

            if (alternativeMatches.isNotEmpty()) {
                SparkCore.LOGGER.debug("找到${alternativeMatches.size}个匹配的替代IK约束: $(potentialIkPath")
                return (potentialIkPath)
            }

            // 如果没有找到匹配的约束，返回null
            return null
        }

        @JvmStatic
        fun of(type: EntityType<*>): ModelIndex {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type) // 处理注册表键可能为空的情况
            val texture = ResourceLocation.fromNamespaceAndPath(key.namespace, "textures/entity/${key.path}.png")
            val ikPath = getDefaultIkPath(key) // 尝试获取默认 IK 路径
            return ModelIndex(key, key, texture, ikPath)
        }

        @JvmStatic
        fun of(item: Item): ModelIndex {
            val key = BuiltInRegistries.ITEM.getKey(item)
            return ModelIndex(key, ResourceLocation.fromNamespaceAndPath(key.namespace, "textures/item/${key.path}.png"))
        }
    }
}