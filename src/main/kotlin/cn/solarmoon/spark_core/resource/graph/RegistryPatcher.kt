package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.resource.common.ModuleIdUtils
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 注册表补丁器
 * 
 * 专门负责与动态注册表的交互和覆盖规则的应用，遵循单一职责原则。
 * 提供统一的注册表操作接口，支持多种资源类型的注册表补丁应用。
 */
object RegistryPatcher {
    
    /**
     * 已应用的覆盖规则映射
     * 键为原始资源位置，值为覆盖后的资源位置
     */
    private val appliedOverrides = ConcurrentHashMap<ResourceLocation, ResourceLocation>()
    
    /**
     * 补丁状态标记
     */
    private var isPatched = false
    
    /**
     * 应用覆盖规则到所有相关的动态注册表
     * 
     * @return 成功应用的覆盖规则数量
     */
    fun applyOverridesToRegistries(): Int {
        if (isPatched) {
            SparkCore.LOGGER.warn("注册表补丁已经应用，跳过重复应用")
            return 0
        }
        
        return try {
            SparkCore.LOGGER.info("开始应用资源覆盖到动态注册表...")
            
            // 获取所有覆盖关系
            val allOverrides = ResourceGraphManager.getAllOverrides()
            var appliedCount = 0
            
            for ((targetNode, overrideNodes) in allOverrides) {
                if (overrideNodes.isEmpty()) continue
                
                val targetResource = targetNode.id
                
                // 使用OverrideManager的解析逻辑找到最终胜者
                val finalResource = OverrideManager.resolveResourceOverride(
                    ResourceGraphManager.getGraph(), 
                    targetResource
                )
                
                if (finalResource != targetResource) {
                    // 应用覆盖到相应的注册表
                    val success = applyOverrideToRegistry(targetResource, finalResource)
                    if (success) {
                        appliedOverrides[targetResource] = finalResource
                        appliedCount++
                        SparkCore.LOGGER.debug("应用覆盖: {} -> {}", targetResource, finalResource)
                    }
                }
            }
            
            isPatched = true
            SparkCore.LOGGER.info("资源覆盖应用完成，共应用 $appliedCount 个覆盖规则")
            appliedCount
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用资源覆盖到注册表时发生错误", e)
            0
        }
    }
    
    /**
     * 将单个覆盖规则应用到相应的注册表
     * 
     * @param originalResource 原始资源位置
     * @param overrideResource 覆盖资源位置
     * @return 是否成功应用
     */
    private fun applyOverrideToRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            // 根据资源路径判断资源类型并应用到相应注册表
            when {
                originalResource.path.contains("animations/") -> {
                    applyToAnimationRegistry(originalResource, overrideResource)
                }
                originalResource.path.contains("models/") -> {
                    applyToModelRegistry(originalResource, overrideResource)
                }
                originalResource.path.contains("textures/") -> {
                    applyToTextureRegistry(originalResource, overrideResource)
                }
                originalResource.path.contains("script/") -> {
                    applyToScriptRegistry(originalResource, overrideResource)
                }
                originalResource.path.contains("ik_constraints/") -> {
                    applyToIKRegistry(originalResource, overrideResource)
                }
                else -> {
                    SparkCore.LOGGER.warn("未知的资源类型，无法应用覆盖: $originalResource")
                    false
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用覆盖规则失败: $originalResource -> $overrideResource", e)
            false
        }
    }
    
    /**
     * 应用到动画注册表
     */
    private fun applyToAnimationRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            val registry = SparkRegistries.TYPED_ANIMATION
            val overrideAnimation = registry.get(overrideResource)
            if (overrideAnimation != null) {
                // 解析模块ID
                val moduleId = ModuleIdUtils.extractModuleIdFromNamespace(originalResource) ?: originalResource.namespace
                
                registry.registerDynamic(originalResource, overrideAnimation, moduleId)
                true
            } else {
                SparkCore.LOGGER.warn("覆盖动画资源不存在: $overrideResource")
                false
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用动画覆盖失败", e)
            false
        }
    }
    
    /**
     * 应用到模型注册表
     */
    private fun applyToModelRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            val registry = SparkRegistries.MODELS ?: return false
            val overrideModel = registry.get(overrideResource)
            if (overrideModel != null) {
                // 解析模块ID
                val moduleId = ModuleIdUtils.extractModuleIdFromNamespace(originalResource) ?: originalResource.namespace
                
                registry.registerDynamic(originalResource, overrideModel, moduleId)
                true
            } else {
                SparkCore.LOGGER.warn("覆盖模型资源不存在: $overrideResource")
                false
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用模型覆盖失败", e)
            false
        }
    }
    
    /**
     * 应用到纹理注册表
     */
    private fun applyToTextureRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            val registry = SparkRegistries.DYNAMIC_TEXTURES ?: return false
            val overrideTexture = registry.get(overrideResource)
            if (overrideTexture != null) {
                // 解析模块ID
                val moduleId = ModuleIdUtils.extractModuleIdFromNamespace(originalResource) ?: originalResource.namespace
                
                registry.registerDynamic(originalResource, overrideTexture, moduleId)
                true
            } else {
                SparkCore.LOGGER.warn("覆盖纹理资源不存在: $overrideResource")
                false
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用纹理覆盖失败", e)
            false
        }
    }
    
    /**
     * 应用到脚本注册表
     */
    private fun applyToScriptRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            false
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用脚本覆盖失败", e)
            false
        }
    }
    
    /**
     * 应用到IK约束注册表
     */
    private fun applyToIKRegistry(originalResource: ResourceLocation, overrideResource: ResourceLocation): Boolean {
        return try {
            val registry = SparkRegistries.IK_COMPONENT_TYPE
            val overrideIK = registry[overrideResource]
            if (overrideIK != null) {
                // 解析模块ID
                val moduleId = ModuleIdUtils.extractModuleIdFromNamespace(originalResource) ?: originalResource.namespace
                
                registry.registerDynamic(originalResource, overrideIK, moduleId)
                true
            } else {
                SparkCore.LOGGER.warn("覆盖IK约束资源不存在: $overrideResource")
                false
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用IK约束覆盖失败", e)
            false
        }
    }
    
    /**
     * 补丁特定注册表
     * 
     * @param registryKey 注册表键
     * @param overrides 覆盖规则映射
     * @return 成功应用的覆盖数量
     */
    fun patchRegistry(registryKey: String, overrides: Map<ResourceLocation, ResourceLocation>): Int {
        var appliedCount = 0
        
        for ((original, override) in overrides) {
            if (applyOverrideToRegistry(original, override)) {
                appliedOverrides[original] = override
                appliedCount++
            }
        }
        
        SparkCore.LOGGER.info("为注册表 $registryKey 应用了 $appliedCount 个覆盖规则")
        return appliedCount
    }
    
    /**
     * 回滚注册表补丁
     * 注意：由于动态注册表的特性，实际的回滚需要重新加载资源
     */
    fun revertRegistryPatches() {
        if (!isPatched) {
            SparkCore.LOGGER.warn("注册表补丁未应用，无需回滚")
            return
        }
        
        SparkCore.LOGGER.info("开始回滚注册表补丁...")
        
        // 清除应用的覆盖记录
        val revertedCount = appliedOverrides.size
        appliedOverrides.clear()
        isPatched = false
        
        SparkCore.LOGGER.info("注册表补丁回滚完成，清除了 $revertedCount 个覆盖记录")
        SparkCore.LOGGER.warn("注意：实际的注册表条目需要通过重新加载资源来恢复")
    }
    
    /**
     * 检查是否已应用补丁
     * 
     * @return 是否已应用补丁
     */
    fun isPatched(): Boolean {
        return isPatched
    }
    
    /**
     * 获取已应用的覆盖规则
     * 
     * @return 覆盖规则映射的只读副本
     */
    fun getAppliedOverrides(): Map<ResourceLocation, ResourceLocation> {
        return appliedOverrides.toMap()
    }
    
    /**
     * 获取补丁统计信息
     * 
     * @return 补丁统计信息
     */
    fun getPatchStatistics(): Map<String, Any> {
        return mapOf(
            "is_patched" to isPatched,
            "applied_overrides_count" to appliedOverrides.size,
            "applied_overrides" to appliedOverrides.toMap()
        )
    }
    
    /**
     * 重置补丁状态，用于测试或重新应用
     */
    fun reset() {
        appliedOverrides.clear()
        isPatched = false
        SparkCore.LOGGER.info("注册表补丁器已重置")
    }
}
