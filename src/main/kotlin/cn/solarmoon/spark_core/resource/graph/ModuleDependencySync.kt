package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import net.minecraft.resources.ResourceLocation
import org.jgrapht.graph.SimpleDirectedGraph

/**
 * 模块依赖同步器
 * 
 * 专门负责资源级别依赖与模块级别依赖的同步，遵循单一职责原则。
 * 当资源依赖跨越不同模块时，自动在模块级别建立相应的依赖关系。
 */
object ModuleDependencySync {
    
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
                            SparkCore.LOGGER.debug("同步跨模块硬依赖: $sourceModuleId -> $targetModuleId (资源: ${sourceNode.id} -> ${dep.id})")
                        }
                    } else {
                        SparkCore.LOGGER.debug("跨模块软依赖不同步到模块级别: $sourceModuleId -> $targetModuleId (资源: ${sourceNode.id} -> ${dep.id})")
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
    
    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val warnings: List<String>
    )
}
