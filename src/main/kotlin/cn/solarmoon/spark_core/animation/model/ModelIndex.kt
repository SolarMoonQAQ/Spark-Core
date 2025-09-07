package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import java.util.*

/**
 * 保存了客户端渲染完整模型所需的必要数据
 */
class ModelIndex (
    val location: ResourceLocation,
    val ikPath: ResourceLocation? = getDefaultIkPath(location)// 添加 IK 路径字段，可为空
) {

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
    
    /**
     * 验证ModelIndex中所有资源的依赖关系
     * 使用ResourceGraphManager的验证框架
     * @return 是否所有依赖都有效
     */
    fun validateDependencies(): Boolean {
        val config = ResourceGraphManager.ValidationConfig(
            checkHardDependencies = true,
            checkSoftDependencies = true,
            checkOptionalDependencies = true,
            logLevel = ResourceGraphManager.ValidationSeverity.IGNORE // 不在这里记录日志
        )

        // 验证模型依赖
        val modelResult = ResourceGraphManager.validateResource(location, config)
        if (!modelResult.isValid) {
            return false
        }

        // 验证IK约束依赖
        ikPath?.let { path ->
            val ikResult = ResourceGraphManager.validateResource(path, config)
            if (!ikResult.isValid) {
                return false
            }
        }

        return true
    }
    
    /**
     * 检查ModelIndex是否具有有效的依赖关系
     * 使用ResourceGraphManager的验证框架
     * @return true表示所有硬依赖都满足
     */
    fun hasValidDependencies(): Boolean {
        // 使用ResourceGraphManager进行快速硬依赖检查
        val modelValid = !ResourceGraphManager.hasHardDependencyFailures(location)
        val ikValid = ikPath?.let { !ResourceGraphManager.hasHardDependencyFailures(it) } ?: true

        return modelValid && ikValid
    }
    
    /**
     * 获取ModelIndex的依赖摘要信息
     * 使用ResourceGraphManager的验证框架
     * @return 包含所有资源依赖信息的描述字符串
     */
    fun getDependencySummary(): String {
        val summary = mutableListOf<String>()

        // 模型依赖
        val modelDeps = ResourceGraphManager.getDirectDependencies(location)
        if (modelDeps.isNotEmpty()) {
            summary.add("模型($location): ${modelDeps.size}个依赖")
        }

        // TODO: 动画依赖

        // IK依赖
        ikPath?.let { path ->
            val ikDeps = ResourceGraphManager.getDirectDependencies(path)
            if (ikDeps.isNotEmpty()) {
                summary.add("IK约束($path): ${ikDeps.size}个依赖")
            }
        }
        
        return if (summary.isEmpty()) {
            "无依赖关系"
        } else {
            summary.joinToString(", ")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ModelIndex) return false
        return other.location == location &&
                other.ikPath == ikPath // 比较 ikPath
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + this@ModelIndex.location.hashCode()
        result = 31 * result + (ikPath?.hashCode() ?: 0) // 计算 ikPath 的哈希码
        return result
    }

    companion object {
        // 定义一个默认的空 IK 路径，或者让 null 代表无 IK
        val EMPTY_IK_PATH: ResourceLocation? = null // 或者 ResourceLocation("spark", "empty_ik")

        // 更新STREAM_CODEC以包含ikPath，使用ByteBufCodecs.optional处理可空的ResourceLocation
        @JvmStatic
        val STREAM_CODEC: StreamCodec<in RegistryFriendlyByteBuf, ModelIndex> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModelIndex::location,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), { Optional.ofNullable(it.ikPath) }
        ) { model, ikPathOpt -> ModelIndex(model, ikPathOpt.orElse(null)) }

        @JvmStatic
        val CODEC: Codec<ModelIndex> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("model_path").forGetter(ModelIndex::location),
                ResourceLocation.CODEC.optionalFieldOf("ik_path").forGetter { mi -> Optional.ofNullable(mi.ikPath) } // 使用 optionalFieldOf 读取 ik_path
            ).apply(instance) { model, ikOpt ->
                ModelIndex(model, ikOpt.orElse(null)) // 如果字段不存在，则 ikPath 为 null
            }
        }


        @JvmStatic
        val EMPTY get() = ModelIndex(
            ResourceLocation.fromNamespaceAndPath("minecraft", "empty"),
            EMPTY_IK_PATH // 使用空 IK 路径
        )

        /**
         * 推断默认 IK 路径的辅助函数
         * 检查是否存在与给定key相关的IK约束
         */
        private fun getDefaultIkPath(key: ResourceLocation): ResourceLocation? {
            // 从模型路径推断IK约束路径
            // 例如：spark_core:spark_core/models/player -> spark_core:spark_core/ik_constraints/player
            val pathParts = key.path.split("/")
            val moduleName = pathParts[0]
            val entityPath = pathParts.drop(2).joinToString("/")
            val potentialIkPath = SparkResourcePathBuilder.buildIKConstraintPath(key.namespace, moduleName, entityPath)

            val alternativeMatches = OIKConstraint.ORIGINS.keys.filter {
                it.toString().startsWith((potentialIkPath.toString()))
            }

            if (alternativeMatches.isNotEmpty()) {
                SparkCore.LOGGER.debug("找到${alternativeMatches.size}个匹配的替代IK约束: $potentialIkPath")
                return potentialIkPath
            }

            // 如果没有找到匹配的约束，返回null
            return null
        }

        @JvmStatic
        fun of(type: EntityType<*>): ModelIndex {
            val id = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            val modelPath = id
            val ikPath = getDefaultIkPath(modelPath) // 使用新的模型路径获取IK路径
            return ModelIndex(modelPath, ikPath)
        }

        @JvmStatic
        fun of(item: Item): ModelIndex {
            val id = BuiltInRegistries.ITEM.getKey(item)
            val modelPath = id
            val ikPath = getDefaultIkPath(modelPath) // 使用新的模型路径获取IK路径
            return ModelIndex(modelPath, ikPath)
        }
    }
}