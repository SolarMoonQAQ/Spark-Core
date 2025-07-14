package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.common.*
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * 重构后的IK约束处理器
 * 使用统一的接口和服务，标准化路径解析和资源管理
 */
@AutoRegisterHandler
class IKConstraintHandler(
    private val ikComponentRegistry: DynamicAwareRegistry<TypedIKComponent>
) : ResourceHandlerBase() {

    companion object {
        init {
            cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService.registerHandler {
                IKConstraintHandler(cn.solarmoon.spark_core.registry.common.SparkRegistries.IK_COMPONENT_TYPE)
            }
        }
    }

    private val resourceType = "ik_constraints"
    private val supportedExtensions = setOf("json")
    private var processedCount = 0

    init {
        SparkCore.LOGGER.info("IKConstraintHandler 初始化完成")
        // 注意：移除对DependencyGraph的注册，使用MetadataManager进行依赖管理
    }
    
    // ===== 基础接口实现 =====
    
    override fun getResourceType(): String = resourceType
    
    override fun getRegistryIdentifier(): ResourceLocation? = ikComponentRegistry.key().location()
    
    override fun getSupportedExtensions(): Set<String> = supportedExtensions
    
    override fun getPriority(): Int = 40 // 中等优先级
    
    // 提供对注册表的访问 (for DynamicResourceApplier)
    val ikComponentRegistryAccess: DynamicAwareRegistry<TypedIKComponent>
        get() = this.ikComponentRegistry
    
    // ===== 资源处理核心逻辑 =====
    
    override fun processResourceAdded(node: ResourceNode) {
        try {
            val ikConstraints = parseIKConstraintFile(node.basePath.resolve(node.relativePath))
            
            // 增加处理计数
            processedCount += ikConstraints.size
            
            // 为每个约束创建资源位置并存储
            ikConstraints.forEachIndexed { index, ikConstraint ->
                val resourceLocation = if (ikConstraints.size == 1) {
                    // 单个约束使用原始资源位置
                    node.id
                } else {
                    // 多个约束使用带索引的资源位置
                    ResourceLocation.fromNamespaceAndPath(
                        node.id.namespace,
                        "${node.id.path}_${index}"
                    )
                }
                
                // 存储到Origin映射
                OIKConstraint.ORIGINS[resourceLocation] = ikConstraint
                
                // 使用完整的模块标识（modId:moduleName格式）
                val moduleId = node.getFullModuleId()

                // 添加到模块资源映射
                addResourceToModule(moduleId, resourceLocation)

                SparkCore.LOGGER.debug("处理IK约束资源: $resourceLocation (模块: $moduleId)")

                registerToRegistry(resourceLocation, ikConstraint)
                // 如果初始扫描完成，注册到动态注册表
                if (isInitialScanComplete()) {
                    // TODO: IK约束增量同步包
                }
            }
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }
    
    override fun processResourceModified(node: ResourceNode) {
        // 修改等同于重新添加
        processResourceAdded(node)
    }
    
    override fun processResourceRemoved(node: ResourceNode) {
        val resourceId = node.id
        // graph的节点删除已在基类中处理

        // 移除可能的所有相关约束（主约束和索引约束）
        val removedConstraints = mutableListOf<ResourceLocation>()
        
        // 移除主约束
        val mainRemoved = OIKConstraint.ORIGINS.remove(resourceId)
        if (mainRemoved != null) {
            removedConstraints.add(resourceId)
        }
        
        // 移除索引约束（如果存在）
        var index = 0
        while (true) {
            val indexedLocation = ResourceLocation.fromNamespaceAndPath(
                resourceId.namespace,
                "${resourceId.path}_${index}"
            )
            val indexedRemoved = OIKConstraint.ORIGINS.remove(indexedLocation)
            if (indexedRemoved != null) {
                removedConstraints.add(indexedLocation)
                index++
            } else {
                break
            }
        }
        
        if (removedConstraints.isNotEmpty()) {
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 从模块资源映射中移除所有相关约束
            removedConstraints.forEach { location ->
                removeResourceFromModule(moduleId, location)
                
                if (isInitialScanComplete()) {
                    unregisterFromRegistry(location)
                }
            }
            
            SparkCore.LOGGER.info("移除了 ${removedConstraints.size} 个IK约束: $removedConstraints")
        } else {
            SparkCore.LOGGER.warn("尝试移除不存在的IK约束: $resourceId")
        }
    }
    
    // ===== IK约束解析 =====
    
    private fun parseIKConstraintFile(filePath: Path): List<OIKConstraint> {
        try {
            val jsonString = filePath.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            
            return when {
                jsonElement.isJsonArray -> {
                    // 处理JSON数组 - 多个约束
                    val ikConstraintsResult = OIKConstraint.LIST_CODEC.decode(JsonOps.INSTANCE, jsonElement).orThrow
                    ikConstraintsResult.first
                }
                jsonElement.isJsonObject -> {
                    // 处理JSON对象 - 单个约束
                    val ikConstraintResult = OIKConstraint.CODEC.decode(JsonOps.INSTANCE, jsonElement).orThrow
                    listOf(ikConstraintResult.first)
                }
                else -> {
                    throw IllegalArgumentException("IK约束文件必须是JSON对象或JSON数组")
                }
            }
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(filePath.toString(), e)
        }
    }
    
    // ===== 注册表操作 =====
    
    private fun registerToRegistry(location: ResourceLocation, ikConstraint: OIKConstraint) {
        try {
            val resourceKey = ResourceKey.create(ikComponentRegistry.key(), location)
            val typedIKComponent = TypedIKComponent(
                id = location,
                chainName = ikConstraint.constraintName,
                startBoneName = ikConstraint.constraintTargetBone,
                endBoneName = ikConstraint.constraintTargetBone, // 需要根据实际业务逻辑调整
                bonePathNames = ikConstraint.ikChainBoneLimits.keys.toList(),
                ikConstraintId = location
            )
            ikComponentRegistry.register(resourceKey, typedIKComponent, RegistrationInfo.BUILT_IN)
            
            SparkCore.LOGGER.debug("IK约束已注册到注册表: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("REGISTER", location.toString(), e)
        }
    }
    
    private fun unregisterFromRegistry(location: ResourceLocation) {
        try {
            val resourceKey = ResourceKey.create(ikComponentRegistry.key(), location)
            ikComponentRegistry.unregisterDynamic(resourceKey)
            
            SparkCore.LOGGER.debug("IK约束已从注册表注销: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("UNREGISTER", location.toString(), e)
        }
    }
    
    // ===== 初始化 =====
    
    override fun initialize(modMainClass: Class<*>):Boolean {
        return try {
            // 发现资源路径
            val resourcePaths = ResourceDiscoveryService.discoverResourcePaths(resourceType)
            
            // 提取默认资源
            val extractionSuccess = extractDefaultResources(modMainClass)
            
            // 扫描并处理现有资源
            for (basePath in resourcePaths) {
                val resourceFiles = ResourceDiscoveryService.scanResourceFiles(basePath, supportedExtensions)
                
                for (resourceFile in resourceFiles) {
                    onResourceAdded(resourceFile)
                }
            }
            
            SparkCore.LOGGER.info("IKConstraintHandler 初始化完成，处理了 $processedCount 个IK约束")
            extractionSuccess
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKConstraintHandler 初始化失败", e)
            false
        }
    }
    
    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        return extractDefaultResources(modMainClass)
    }
    
    private fun extractDefaultResources(modMainClass: Class<*>): Boolean {
        return MultiModuleResourceExtractionUtil.extractAllModuleResources(
            modMainClass,
            resourceType
        )
    }
    
    // ===== 模块清理 =====
    
    override fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 清理主约束
        OIKConstraint.ORIGINS.remove(resourceLocation)
        if (isInitialScanComplete()) {
            unregisterFromRegistry(resourceLocation)
        }
        
        // 清理索引约束（如果存在）
        var index = 0
        while (true) {
            val indexedLocation = ResourceLocation.fromNamespaceAndPath(
                resourceLocation.namespace,
                "${resourceLocation.path}_${index}"
            )
            val removed = OIKConstraint.ORIGINS.remove(indexedLocation)
            if (removed != null) {
                if (isInitialScanComplete()) {
                    unregisterFromRegistry(indexedLocation)
                }
                index++
            } else {
                break
            }
        }
    }
}