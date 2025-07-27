package cn.solarmoon.spark_core.web.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.adapter.DependencyGraphData
import cn.solarmoon.spark_core.web.adapter.DependencyValidationResult
import cn.solarmoon.spark_core.web.adapter.WebApiAdapter
import cn.solarmoon.spark_core.web.dto.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * 依赖编辑服务
 * 
 * 提供依赖关系的增删改查和验证功能的业务逻辑处理
 * 处理依赖操作的事务性、验证、错误处理等。
 */
object DependencyEditService {
    
    /**
     * 获取资源的依赖关系图
     * 
     * @param resourceId 资源ID
     * @param includeIndirect 是否包含间接依赖
     * @param hardOnly 是否只包含硬依赖
     * @return API响应，包含依赖图数据
     */
    suspend fun getDependencyGraph(
        resourceId: String,
        includeIndirect: Boolean = true,
        hardOnly: Boolean = false
    ): ApiResponse<DependencyGraphData> {
        return withContext(Dispatchers.IO) {
            try {
                val dependencyGraph = WebApiAdapter.getDependencyGraph(resourceId, includeIndirect, hardOnly)
                ApiResponse.success(dependencyGraph, "依赖图获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取依赖图失败", e)
                ApiResponse.error("获取依赖图失败: ${e.message}")
            }
        }
    }
    
    /**
     * 添加依赖关系
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @param dependencyType 依赖类型 (HARD, SOFT, OPTIONAL)
     * @param validateFirst 是否先验证依赖关系
     * @return API响应，包含操作结果
     */
    suspend fun addDependency(
        sourceId: String,
        targetId: String,
        dependencyType: String,
        validateFirst: Boolean = true
    ): ApiResponse<DependencyOperationResult> {
        return withContext(Dispatchers.IO) {
            try {
                // 验证依赖关系
                if (validateFirst) {
                    val validationResult = WebApiAdapter.validateDependency(sourceId, targetId)
                    if (!validationResult.isValid) {
                        return@withContext ApiResponse.error<DependencyOperationResult>(
                            "依赖验证失败: ${validationResult.errorMessage}"
                        )
                    }
                }
                
                // 添加依赖
                val success = WebApiAdapter.addDependency(sourceId, targetId, dependencyType)
                
                if (success) {
                    val result = DependencyOperationResult(
                        success = true,
                        sourceId = sourceId,
                        targetId = targetId,
                        operation = "ADD",
                        dependencyType = dependencyType,
                        message = "依赖关系添加成功"
                    )
                    ApiResponse.success(result, "依赖关系添加成功")
                } else {
                    ApiResponse.error("依赖关系添加失败，可能已存在相同依赖")
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("添加依赖关系失败", e)
                ApiResponse.error("添加依赖关系失败: ${e.message}")
            }
        }
    }
    
    /**
     * 移除依赖关系
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @return API响应，包含操作结果
     */
    suspend fun removeDependency(
        sourceId: String,
        targetId: String
    ): ApiResponse<DependencyOperationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val success = WebApiAdapter.removeDependency(sourceId, targetId)
                
                if (success) {
                    val result = DependencyOperationResult(
                        success = true,
                        sourceId = sourceId,
                        targetId = targetId,
                        operation = "REMOVE",
                        dependencyType = null,
                        message = "依赖关系移除成功"
                    )
                    ApiResponse.success(result, "依赖关系移除成功")
                } else {
                    ApiResponse.error("依赖关系移除失败，可能不存在此依赖")
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("移除依赖关系失败", e)
                ApiResponse.error("移除依赖关系失败: ${e.message}")
            }
        }
    }
    
    /**
     * 验证依赖关系
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @return API响应，包含验证结果
     */
    suspend fun validateDependency(
        sourceId: String,
        targetId: String
    ): ApiResponse<DependencyValidationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val validationResult = WebApiAdapter.validateDependency(sourceId, targetId)
                ApiResponse.success(validationResult, "依赖验证完成")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("验证依赖关系失败", e)
                ApiResponse.error("验证依赖关系失败: ${e.message}")
            }
        }
    }
    
    /**
     * 批量添加依赖关系
     * 
     * @param operations 批量操作列表
     * @return API响应，包含批量操作结果
     */
    suspend fun batchAddDependencies(
        operations: List<DependencyOperation>
    ): ApiResponse<BatchDependencyResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<DependencyOperationResult>()
                var successCount = 0
                var failureCount = 0
                
                operations.forEach { operation ->
                    try {
                        // 验证依赖关系
                        val validationResult = WebApiAdapter.validateDependency(
                            operation.sourceId, 
                            operation.targetId
                        )
                        
                        if (validationResult.isValid) {
                            val success = WebApiAdapter.addDependency(
                                operation.sourceId,
                                operation.targetId,
                                operation.dependencyType
                            )
                            
                            if (success) {
                                successCount++
                                results.add(
                                    DependencyOperationResult(
                                        success = true,
                                        sourceId = operation.sourceId,
                                        targetId = operation.targetId,
                                        operation = "ADD",
                                        dependencyType = operation.dependencyType,
                                        message = "成功"
                                    )
                                )
                            } else {
                                failureCount++
                                results.add(
                                    DependencyOperationResult(
                                        success = false,
                                        sourceId = operation.sourceId,
                                        targetId = operation.targetId,
                                        operation = "ADD",
                                        dependencyType = operation.dependencyType,
                                        message = "添加失败，可能已存在"
                                    )
                                )
                            }
                        } else {
                            failureCount++
                            results.add(
                                DependencyOperationResult(
                                    success = false,
                                    sourceId = operation.sourceId,
                                    targetId = operation.targetId,
                                    operation = "ADD",
                                    dependencyType = operation.dependencyType,
                                    message = "验证失败: ${validationResult.errorMessage}"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        failureCount++
                        results.add(
                            DependencyOperationResult(
                                success = false,
                                sourceId = operation.sourceId,
                                targetId = operation.targetId,
                                operation = "ADD",
                                dependencyType = operation.dependencyType,
                                message = "异常: ${e.message}"
                            )
                        )
                    }
                }
                
                val batchResult = BatchDependencyResult(
                    totalOperations = operations.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    results = results
                )
                
                ApiResponse.success(batchResult, "批量操作完成: 成功 $successCount 个，失败 $failureCount 个")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量添加依赖失败", e)
                ApiResponse.error("批量添加依赖失败: ${e.message}")
            }
        }
    }
    
    /**
     * 批量移除依赖关系
     * 
     * @param operations 批量操作列表
     * @return API响应，包含批量操作结果
     */
    suspend fun batchRemoveDependencies(
        operations: List<DependencyRemoveOperation>
    ): ApiResponse<BatchDependencyResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<DependencyOperationResult>()
                var successCount = 0
                var failureCount = 0
                
                operations.forEach { operation ->
                    try {
                        val success = WebApiAdapter.removeDependency(
                            operation.sourceId,
                            operation.targetId
                        )
                        
                        if (success) {
                            successCount++
                            results.add(
                                DependencyOperationResult(
                                    success = true,
                                    sourceId = operation.sourceId,
                                    targetId = operation.targetId,
                                    operation = "REMOVE",
                                    dependencyType = null,
                                    message = "成功"
                                )
                            )
                        } else {
                            failureCount++
                            results.add(
                                DependencyOperationResult(
                                    success = false,
                                    sourceId = operation.sourceId,
                                    targetId = operation.targetId,
                                    operation = "REMOVE",
                                    dependencyType = null,
                                    message = "移除失败，可能不存在"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        failureCount++
                        results.add(
                            DependencyOperationResult(
                                success = false,
                                sourceId = operation.sourceId,
                                targetId = operation.targetId,
                                operation = "REMOVE",
                                dependencyType = null,
                                message = "异常: ${e.message}"
                            )
                        )
                    }
                }
                
                val batchResult = BatchDependencyResult(
                    totalOperations = operations.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    results = results
                )
                
                ApiResponse.success(batchResult, "批量操作完成: 成功 $successCount 个，失败 $failureCount 个")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量移除依赖失败", e)
                ApiResponse.error("批量移除依赖失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取依赖关系建议
     * 基于资源类型和现有依赖模式提供智能建议
     * 
     * @param resourceId 资源ID
     * @return API响应，包含依赖建议
     */
    suspend fun getDependencySuggestions(resourceId: String): ApiResponse<List<DependencySuggestion>> {
        return withContext(Dispatchers.IO) {
            try {
                // 这里可以实现智能依赖建议算法
                // 基于资源类型、命名模式、现有依赖等因素
                val suggestions = mutableListOf<DependencySuggestion>()
                
                // 简单示例：基于资源类型的建议
                val resourceLocation = net.minecraft.resources.ResourceLocation.parse(resourceId)
                val node = cn.solarmoon.spark_core.resource.graph.ResourceNodeManager.getNode(resourceLocation)
                
                if (node != null) {
                    val resourceType = inferResourceType(node.id.path)
                    val resourceName = extractResourceName(node.id.path)
                    
                    // 根据资源类型提供建议
                    when (resourceType) {
                        "models" -> {
                            // 模型可能需要贴图和动画
                            val textureId = "${node.namespace}:${node.moduleName}/textures/$resourceName"
                            val animationId = "${node.namespace}:${node.moduleName}/animations/$resourceName"
                            
                            suggestions.add(
                                DependencySuggestion(
                                    targetId = textureId,
                                    dependencyType = "SOFT",
                                    reason = "模型通常需要对应的贴图文件",
                                    confidence = 0.8
                                )
                            )
                            
                            suggestions.add(
                                DependencySuggestion(
                                    targetId = animationId,
                                    dependencyType = "SOFT",
                                    reason = "模型可能需要对应的动画文件",
                                    confidence = 0.6
                                )
                            )
                        }
                        "scripts" -> {
                            // 脚本可能需要模型
                            val modelId = "${node.namespace}:${node.moduleName}/models/$resourceName"
                            suggestions.add(
                                DependencySuggestion(
                                    targetId = modelId,
                                    dependencyType = "HARD",
                                    reason = "脚本通常需要对应的模型文件",
                                    confidence = 0.9
                                )
                            )
                        }
                    }
                }
                
                ApiResponse.success(suggestions, "依赖建议生成完成")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取依赖建议失败", e)
                ApiResponse.error("获取依赖建议失败: ${e.message}")
            }
        }
    }
    
    /**
     * 从资源路径中推断资源类型
     */
    private fun inferResourceType(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 1) {
            parts[1] // 四层结构中的resourceType
        } else {
            "unknown"
        }
    }
    
    /**
     * 从资源路径中提取资源名称
     */
    private fun extractResourceName(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 2) {
            parts.drop(2).joinToString("/") // 去掉moduleName和resourceType
        } else {
            path
        }
    }
}

/**
 * 依赖操作
 */
data class DependencyOperation(
    val sourceId: String,
    val targetId: String,
    val dependencyType: String
)

/**
 * 依赖移除操作
 */
data class DependencyRemoveOperation(
    val sourceId: String,
    val targetId: String
)

/**
 * 依赖操作结果
 */
data class DependencyOperationResult(
    val success: Boolean,
    val sourceId: String,
    val targetId: String,
    val operation: String,
    val dependencyType: String?,
    val message: String
)

/**
 * 批量依赖操作结果
 */
data class BatchDependencyResult(
    val totalOperations: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<DependencyOperationResult>
)

/**
 * 依赖建议
 */
data class DependencySuggestion(
    val targetId: String,
    val dependencyType: String,
    val reason: String,
    val confidence: Double
)
