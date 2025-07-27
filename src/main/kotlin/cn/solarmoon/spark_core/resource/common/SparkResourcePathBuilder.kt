package cn.solarmoon.spark_core.resource.common

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

/**
 * SparkCore通用资源路径构建器
 * 
 * 为所有mod提供统一的资源路径构建服务，支持多mod多模块架构
 * 路径格式：{modId}:{moduleName}/{resourceType}/{path}
 */
object SparkResourcePathBuilder {
    
    /**
     * 构建动画资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @param animationName 动画名称
     * @return ResourceLocation格式：{modId}:{moduleName}/animations/{entityPath}/{animationName}
     */
    fun buildAnimationPath(
        modId: String,
        moduleName: String,
        entityPath: String,
        animationName: String
    ): ResourceLocation {
        val path = "$moduleName/animations/$entityPath/$animationName"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    /**
     * 构建模型动画路径
     *
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @return ResourceLocation格式：{modId}:{moduleName}/animations/{entityPath}
     */
    fun buildAnimationPath(
        modId: String,
        moduleName: String,
        entityPath: String
    ): ResourceLocation {
        val path = "$moduleName/animations/$entityPath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    /**
     * 构建模型资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @return ResourceLocation格式：{modId}:{moduleName}/models/{entityPath}
     */
    fun buildModelPath(
        modId: String,
        moduleName: String,
        entityPath: String
    ): ResourceLocation {
        val path = "$moduleName/models/$entityPath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    
    /**
     * 构建贴图资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param texturePath 贴图路径
     * @return ResourceLocation格式：{modId}:{moduleName}/textures/{texturePath}
     */
    fun buildTexturePath(
        modId: String,
        moduleName: String,
        texturePath: String
    ): ResourceLocation {
        val path = "$moduleName/textures/$texturePath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    
    /**
     * 构建脚本资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param scriptPath 脚本路径
     * @return ResourceLocation格式：{modId}:{moduleName}/scripts/{scriptPath}
     */
    fun buildScriptPath(
        modId: String,
        moduleName: String,
        scriptPath: String
    ): ResourceLocation {
        val path = "$moduleName/scripts/$scriptPath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    
    /**
     * 构建IK约束资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param constraintPath 约束路径
     * @return ResourceLocation格式：{modId}:{moduleName}/ik_constraints/{constraintPath}
     */
    fun buildIKConstraintPath(
        modId: String,
        moduleName: String,
        constraintPath: String
    ): ResourceLocation {
        val path = "$moduleName/ik_constraints/$constraintPath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    
    /**
     * 通用资源路径构建器
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param resourceType 资源类型（如 "animations", "models", "textures"）
     * @param resourcePath 资源路径
     * @return ResourceLocation格式：{modId}:{moduleName}/{resourceType}/{resourcePath}
     */
    fun buildResourcePath(
        modId: String,
        moduleName: String,
        resourceType: String,
        resourcePath: String
    ): ResourceLocation {
        val path = "$moduleName/$resourceType/$resourcePath"
        return ResourceLocation.fromNamespaceAndPath(modId, path)
    }
    
    /**
     * 从实体类型构建动画路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityType 实体类型
     * @param animationName 动画名称
     * @return 动画ResourceLocation
     */
    fun buildAnimationPathFromEntity(
        modId: String,
        moduleName: String,
        entityType: EntityType<*>,
        animationName: String
    ): ResourceLocation {
        val entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        val entityPath = extractEntityPath(entityKey)
        return buildAnimationPath(modId, moduleName, entityPath, animationName)
    }
    
    /**
     * 从实体类型构建模型路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityType 实体类型
     * @return 模型ResourceLocation
     */
    fun buildAnimationPathFromEntity(
        modId: String,
        moduleName: String,
        entityType: EntityType<*>
    ): ResourceLocation {
        val entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        val entityPath = extractEntityPath(entityKey)
        return buildAnimationPath(modId, moduleName, entityPath)
    }
    /**
     * 从实体类型构建动画路径
     *
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityType 实体类型
     * @return 模型ResourceLocation
     */
    fun buildModelPathFromEntity(
        modId: String,
        moduleName: String,
        entityType: EntityType<*>
    ): ResourceLocation {
        val entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        val entityPath = extractEntityPath(entityKey)
        return buildModelPath(modId, moduleName, entityPath)
    }
    /**
     * 从物品类型构建动画路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param item 物品类型
     * @return 模型ResourceLocation
     */
    fun buildModelPathFromItem(
        modId: String,
        moduleName: String,
        item: Item
    ): ResourceLocation {
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        val itemPath = extractItemPath(itemKey)
        return buildModelPath(modId, moduleName, itemPath)
    }
    
    /**
     * 提取实体路径（移除命名空间）
     */
    private fun extractEntityPath(entityKey: ResourceLocation): String {
        return entityKey.path
    }
    
    /**
     * 提取物品路径（移除命名空间）
     */
    private fun extractItemPath(itemKey: ResourceLocation): String {
        return itemKey.path
    }
    
    /**
     * 路径规范化（确保符合Minecraft ResourceLocation规范）
     */
    fun normalizePath(path: String): String {
        return path.lowercase()
            .replace(" ", "_")
            .replace("-", "_")
            .filter { it.isLetterOrDigit() || it in "/_." }
    }

    // ========== ModId推断和向后兼容性支持 ==========

    /**
     * 从实体类型推断ModId
     */
    fun inferModIdFromEntityType(entityType: EntityType<*>): String {
        val entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        return entityKey.namespace
    }

    /**
     * 从物品类型推断ModId
     */
    fun inferModIdFromItem(item: Item): String {
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        return itemKey.namespace
    }



    /**
     * 检查是否是默认的player模型路径（支持多mod）
     */
    fun isDefaultPlayerModel(modelPath: ResourceLocation): Boolean {
        return when {
            // 原版minecraft:player
            modelPath.namespace == "minecraft" && modelPath.path == "player" -> true
            // 新格式的各种player模型路径
            modelPath.path.endsWith("/models/player") -> true
            modelPath.path == "models/player" -> true
            else -> false
        }
    }

    // ========== 兼容方法：从模型路径转换为动画路径  ==========
    fun buildAnimationPathFromModel(modelPath: ResourceLocation): ResourceLocation {
        val path = modelPath.path
        // 简单把models替换成animations
        return ResourceLocation.fromNamespaceAndPath(modelPath.namespace, path.replace("models", "animations"))
    }

    // ========== 便利方法：自动推断ModId ==========

    /**
     * 自动推断ModId并构建实体动画路径
     * minecraft实体映射到spark_core，其他使用{modId}:{modId}格式
     * 返回基础动画路径，不包含具体的动画集名称
     */
    fun buildAnimationPathFromEntityAuto(entityType: EntityType<*>): ResourceLocation {
        val originalModId = inferModIdFromEntityType(entityType)
        val targetModId = if (originalModId == "minecraft") "spark_core" else originalModId
        val moduleName = targetModId // 模块名就是modId
        return buildAnimationPathFromEntity(targetModId, moduleName, entityType)
    }

    /**
     * 自动推断ModId并构建实体模型路径
     * minecraft实体映射到spark_core，其他使用{modId}:{modId}格式
     */
    fun buildModelPathFromEntityAuto(entityType: EntityType<*>): ResourceLocation {
        val originalModId = inferModIdFromEntityType(entityType)
        val targetModId = if (originalModId == "minecraft") "spark_core" else originalModId
        val moduleName = targetModId // 模块名就是modId
        return buildModelPathFromEntity(targetModId, moduleName, entityType)
    }

    /**
     * 自动推断ModId并构建实体贴图路径
     * minecraft实体映射到spark_core，其他使用{modId}:{modId}格式
     */
    fun buildTexturePathFromEntityAuto(entityType: EntityType<*>): ResourceLocation {
        val originalModId = inferModIdFromEntityType(entityType)
        val targetModId = if (originalModId == "minecraft") "spark_core" else originalModId
        val moduleName = targetModId  // 模块名就是modId
        val entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        val entityPath = entityKey.path
        return buildTexturePath(targetModId, moduleName, "entity/$entityPath")
    }



    /**
     * 自动推断ModId并构建物品模型路径
     * 使用{modId}:{modId}格式
     */
    fun buildModelPathFromItemAuto(item: Item): ResourceLocation {
        val modId = inferModIdFromItem(item)
        val moduleName = modId // 模块名就是modId
        return buildModelPathFromItem(modId, moduleName, item)
    }

    /**
     * 自动推断ModId并构建物品动画路径
     * 使用{modId}:{modId}格式
     * 返回基础动画路径，不包含具体的动画集名称
     */
    fun buildAnimationPathFromItemAuto(item: Item): ResourceLocation {
        val modId = inferModIdFromItem(item)
        val moduleName = modId // 模块名就是modId
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        val itemPath = extractItemPath(itemKey)
        return buildAnimationPath(modId, moduleName, itemPath, "")
            .let { ResourceLocation.fromNamespaceAndPath(it.namespace, it.path.substringBeforeLast("/")) }
    }

    /**
     * 自动推断ModId并构建物品贴图路径
     * 使用{modId}:{modId}格式
     */
    fun buildTexturePathFromItemAuto(item: Item): ResourceLocation {
        val modId = inferModIdFromItem(item)
        val moduleName = modId // 模块名就是modId
        val itemKey = BuiltInRegistries.ITEM.getKey(item)
        val itemPath = itemKey.path
        return buildTexturePath(modId, moduleName, "item/$itemPath")
    }


}
