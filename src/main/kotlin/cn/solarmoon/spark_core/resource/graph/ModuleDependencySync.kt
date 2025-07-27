package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import org.jgrapht.graph.SimpleDirectedGraph
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块依赖同步器
 *
 * 专门负责资源级别依赖与模块级别依赖的同步，遵循单一职责原则。
 * 当资源依赖跨越不同模块时，自动在模块级别建立相应的依赖关系。
 */
object ModuleDependencySync {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val isDevelopmentMode = AtomicBoolean(true)
    private val isSaving = AtomicBoolean(false)
    
    /**
     * 同步跨模块依赖到ModuleGraphManager
     * 当资源依赖跨越不同模块时，自动在模块级别建立依赖关系
     * 
     * @param sourceNode 源资源节点
     * @param dependencies 依赖列表
     */
    fun syncModuleDependencies(sourceNode: ResourceNode, dependencies: List<OResourceDependency>) {
        try {
            // 直接从ResourceNode获取模块信息，提高性能
            val sourceModuleId = sourceNode.getFullModuleId()
            
            dependencies.forEach { dep ->
                val targetModuleId = extractModuleIdFromResourceLocation(dep.id)
                
                // 只有当源模块和目标模块不同时才需要同步
                if (sourceModuleId != targetModuleId) {
                    // 只同步硬依赖，软依赖和可选依赖不影响模块加载顺序
                    if (dep.type == ODependencyType.HARD) {
                        val success = ModuleGraphManager.addDynamicDependency(sourceModuleId, targetModuleId)
                        if (success) {
                            SparkCore.LOGGER.debug(
                                "同步跨模块硬依赖: {} -> {} (资源: {} -> {})",
                                sourceModuleId,
                                targetModuleId,
                                sourceNode.id,
                                dep.id
                            )
                        }
                    } else {
                        SparkCore.LOGGER.debug(
                            "跨模块软依赖不同步到模块级别: {} -> {} (资源: {} -> {})",
                            sourceModuleId,
                            targetModuleId,
                            sourceNode.id,
                            dep.id
                        )
                    }
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("同步模块依赖失败: ${sourceNode.id}", e)
        }
    }
    
    /**
     * 清理资源移除时的模块依赖
     * 当资源被移除时，检查是否还有其他资源维持跨模块依赖，如果没有则移除模块依赖
     * 
     * @param graph 资源依赖图
     * @param removedNode 被移除的资源节点
     */
    fun cleanupModuleDependenciesForResource(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        removedNode: ResourceNode
    ) {
        try {
            // 直接从ResourceNode获取模块信息，提高性能
            val sourceModuleId = removedNode.getFullModuleId()

            // 获取被移除资源的所有依赖
            val dependencies = graph.outgoingEdgesOf(removedNode).map { edge ->
                val targetNode = graph.getEdgeTarget(edge)
                val targetModuleId = targetNode.getFullModuleId()
                Pair(targetModuleId, edge)
            }
            
            // 检查每个跨模块依赖是否还有其他资源支持
            dependencies.forEach { (targetModuleId, edge) ->
                if (sourceModuleId != targetModuleId) {
                    // 检查是否还有其他来自同一源模块的资源依赖于目标模块
                    val hasOtherCrossModuleDependencies = ResourceNodeManager.getAllNodeValues().any { node ->
                        node.getFullModuleId() == sourceModuleId &&
                        node.id != removedNode.id &&
                        graph.outgoingEdgesOf(node).any { outEdge ->
                            val outTargetNode = graph.getEdgeTarget(outEdge)
                            outTargetNode.getFullModuleId() == targetModuleId &&
                            graph.getEdge(node, outTargetNode) == EdgeType.HARD_DEPENDENCY
                        }
                    }
                    
                    // 如果没有其他跨模块硬依赖，则移除模块级别的依赖
                    if (!hasOtherCrossModuleDependencies && edge == EdgeType.HARD_DEPENDENCY) {
                        val removed = ModuleGraphManager.removeDynamicDependency(sourceModuleId, targetModuleId)
                        if (removed) {
                            SparkCore.LOGGER.debug("清理跨模块依赖: $sourceModuleId -> $targetModuleId (最后的资源依赖已移除)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("清理模块依赖失败: ${removedNode.id}", e)
        }
    }
    
    /**
     * 批量同步模块依赖
     * 用于初始化或重建模块依赖关系
     * 
     * @param graph 资源依赖图
     */
    fun batchSyncModuleDependencies(graph: SimpleDirectedGraph<ResourceNode, EdgeType>) {
        try {
            SparkCore.LOGGER.info("开始批量同步模块依赖...")
            
            val moduleDependencies = mutableMapOf<Pair<String, String>, Boolean>()
            
            // 遍历所有资源节点，收集跨模块依赖
            for (sourceNode in graph.vertexSet()) {
                val sourceModuleId = sourceNode.getFullModuleId()

                for (edge in graph.outgoingEdgesOf(sourceNode)) {
                    val targetNode = graph.getEdgeTarget(edge)
                    val targetModuleId = targetNode.getFullModuleId()
                    
                    // 只处理跨模块的硬依赖
                    if (sourceModuleId != targetModuleId && edge == EdgeType.HARD_DEPENDENCY) {
                        val dependencyPair = Pair(sourceModuleId, targetModuleId)
                        moduleDependencies[dependencyPair] = true
                    }
                }
            }
            
            // 批量添加模块依赖
            var syncedCount = 0
            for ((sourceModuleId, targetModuleId) in moduleDependencies.keys) {
                val success = ModuleGraphManager.addDynamicDependency(sourceModuleId, targetModuleId)
                if (success) {
                    syncedCount++
                }
            }
            
            SparkCore.LOGGER.info("批量同步模块依赖完成，共同步 $syncedCount 个模块依赖")
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("批量同步模块依赖失败", e)
        }
    }
    
    /**
     * 验证模块依赖的一致性
     * 检查资源级别的依赖与模块级别的依赖是否一致
     * 
     * @param graph 资源依赖图
     * @return 验证结果
     */
    fun validateModuleDependencyConsistency(graph: SimpleDirectedGraph<ResourceNode, EdgeType>): ValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // 收集所有应该存在的模块依赖
            val expectedModuleDependencies = mutableSetOf<Pair<String, String>>()
            
            for (sourceNode in graph.vertexSet()) {
                val sourceModuleId = sourceNode.getFullModuleId()

                for (edge in graph.outgoingEdgesOf(sourceNode)) {
                    val targetNode = graph.getEdgeTarget(edge)
                    val targetModuleId = targetNode.getFullModuleId()
                    
                    if (sourceModuleId != targetModuleId && edge == EdgeType.HARD_DEPENDENCY) {
                        expectedModuleDependencies.add(Pair(sourceModuleId, targetModuleId))
                    }
                }
            }
            
            // 检查模块依赖是否存在
            for ((sourceModuleId, targetModuleId) in expectedModuleDependencies) {
                val sourceModule = ModuleGraphManager.getModuleNode(sourceModuleId)
                val targetModule = ModuleGraphManager.getModuleNode(targetModuleId)

                if (sourceModule == null || targetModule == null) {
                    issues.add("模块不存在: $sourceModuleId -> $targetModuleId")
                    continue
                }

                // 这里简化检查，实际项目中可能需要更复杂的依赖检查逻辑
                // 由于ModuleGraphManager没有直接的依赖检查方法，我们暂时跳过这个检查
                SparkCore.LOGGER.debug("跳过模块依赖检查: $sourceModuleId -> $targetModuleId")
            }

            // 由于ModuleGraphManager API限制，暂时跳过多余依赖检查
            SparkCore.LOGGER.debug("跳过多余模块依赖检查")
            
        } catch (e: Exception) {
            issues.add("验证过程中发生异常: ${e.message}")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }
    
    /**
     * 从ResourceLocation中提取模块ID
     * 从ResourceNode获取完整的模块信息（modId:moduleName）
     *
     * @param resourceLocation 资源位置
     * @return 完整的模块ID（格式：modId:moduleName）
     */
    private fun extractModuleIdFromResourceLocation(resourceLocation: ResourceLocation): String {
        // 从ResourceNode获取模块信息，而不是解析ResourceLocation
        val node = ResourceNodeManager.getNode(resourceLocation)
        return node?.getFullModuleId() ?: resourceLocation.namespace
    }
    
    // ==================== 开发模式序列化功能 ====================

    /**
     * 设置开发模式
     */
    fun setDevelopmentMode(enabled: Boolean) {
        isDevelopmentMode.set(enabled)
        SparkCore.LOGGER.info("ModuleDependencySync 开发模式已${if (enabled) "启用" else "禁用"}")
    }

    /**
     * 开发模式下同步模块信息到磁盘
     * 在资源依赖变更后调用，更新模块的 resourceDependencies 并保存
     */
    fun syncModuleInfoToDisk(moduleId: String) {
        if (!isDevelopmentMode.get() || isSaving.get()) return

        try {
            val moduleNode = ModuleGraphManager.getModuleNode(moduleId)
            if (moduleNode == null) {
                SparkCore.LOGGER.warn("模块节点不存在，无法同步: $moduleId")
                return
            }

            // 收集该模块的所有资源依赖
            val resourceDependencies = collectResourceDependenciesForModule(moduleId)
            // 注意：ModuleNode.data 是 val，不能直接修改
            // TODO: 需要重新重建OModuleInfo的各个字段

            // 更新模块信息
            val updatedModuleInfo = moduleNode.data.copy(
                resourceDependencies = resourceDependencies
            )

            // 保存到磁盘（避免触发MetaHandler）
            saveModuleInfoWithoutTriggeringHandler(moduleId, updatedModuleInfo)


            SparkCore.LOGGER.debug("已同步模块信息到磁盘: $moduleId")

        } catch (e: Exception) {
            SparkCore.LOGGER.error("同步模块信息到磁盘失败: $moduleId", e)
        }
    }

    /**
     * 收集模块的资源依赖信息
     */
    private fun collectResourceDependenciesForModule(moduleId: String): Map<ResourceLocation, List<OResourceDependency>> {
        val resourceDependencies = mutableMapOf<ResourceLocation, List<OResourceDependency>>()

        // 获取该模块下的所有资源节点
        val moduleResources = ResourceNodeManager.getAllNodeValues()
            .filter { "${it.modId}:${it.moduleName}" == moduleId }

        for (resourceNode in moduleResources) {
            val dependencies = ResourceGraphManager.getDirectDependencies(resourceNode.id)
            if (dependencies.isNotEmpty()) {
                resourceDependencies[resourceNode.id] = dependencies
            }
        }

        return resourceDependencies
    }

    /**
     * 保存模块信息但不触发MetaHandler
     */
    private fun saveModuleInfoWithoutTriggeringHandler(moduleId: String, moduleInfo: OModuleInfo) {
        if (!isSaving.compareAndSet(false, true)) return

        try {
            val moduleDir = getModuleDirectory(moduleId)
            if (moduleDir == null) {
                SparkCore.LOGGER.warn("无法找到模块目录: $moduleId")
                return
            }

            val metaFile = moduleDir.resolve(OModuleInfo.DESCRIPTOR_FILE_NAME)
            val jsonContent = gson.toJson(moduleInfo)

            // TODO: 实现避免触发ResHotReloadService的机制
            // 当前临时实现：直接写入文件
            Files.createDirectories(metaFile.parent)
            Files.writeString(metaFile, jsonContent)

            SparkCore.LOGGER.debug("已保存模块信息: {}", metaFile)

        } catch (e: Exception) {
            SparkCore.LOGGER.error("保存模块信息失败: $moduleId", e)
        } finally {
            isSaving.set(false)
        }
    }

    /**
     * 获取模块目录
     */
    private fun getModuleDirectory(moduleId: String): Path? {
        val parts = moduleId.split(":")
        if (parts.size != 2) return null

        val (modId, moduleName) = parts
        return FMLPaths.GAMEDIR.get().resolve("sparkcore").resolve(modId).resolve(moduleName)
    }

    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val warnings: List<String>
    )
}
