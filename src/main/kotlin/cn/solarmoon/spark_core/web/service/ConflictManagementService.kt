package cn.solarmoon.spark_core.web.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.conflict.ResourceConflictManager
import cn.solarmoon.spark_core.web.dto.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * 冲突管理服务
 * 
 * 提供冲突检测、解决、批量处理等功能的业务逻辑处理
 * 处理冲突操作的事务性、错误处理、异步操作等。
 */
object ConflictManagementService {
    
    /**
     * 获取所有冲突列表
     * 
     * @return API响应，包含冲突列表
     */
    suspend fun getAllConflicts(): ApiResponse<List<ConflictInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val conflicts = ResourceConflictManager.getAllConflicts()
                val conflictList = conflicts.map { (resourceId, conflictInfo) ->
                    ConflictInfo(
                        resourceId = resourceId,
                        resourceName = extractResourceName(conflictInfo.resourceNode.id.path),
                        namespace = conflictInfo.resourceNode.namespace,
                        modId = conflictInfo.resourceNode.modId,
                        moduleName = conflictInfo.resourceNode.moduleName,
                        resourceType = inferResourceType(conflictInfo.resourceNode.id.path),
                        currentFile = conflictInfo.currentFile.toString(),
                        legacyFile = conflictInfo.legacyFile.toString(),
                        detectedTime = conflictInfo.detectedTime
                    )
                }
                
                ApiResponse.success(conflictList, "冲突列表获取成功，共 ${conflictList.size} 个冲突")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取冲突列表失败", e)
                ApiResponse.error("获取冲突列表失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取特定模块的冲突
     * 
     * @param moduleId 模块ID (格式: modId:moduleName)
     * @return API响应，包含模块冲突列表
     */
    suspend fun getModuleConflicts(moduleId: String): ApiResponse<List<ConflictInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val conflicts = ResourceConflictManager.getModuleConflicts(moduleId)
                val conflictList = conflicts.map { conflictInfo ->
                    ConflictInfo(
                        resourceId = conflictInfo.resourceNode.id.toString(),
                        resourceName = extractResourceName(conflictInfo.resourceNode.id.path),
                        namespace = conflictInfo.resourceNode.namespace,
                        modId = conflictInfo.resourceNode.modId,
                        moduleName = conflictInfo.resourceNode.moduleName,
                        resourceType = inferResourceType(conflictInfo.resourceNode.id.path),
                        currentFile = conflictInfo.currentFile.toString(),
                        legacyFile = conflictInfo.legacyFile.toString(),
                        detectedTime = conflictInfo.detectedTime
                    )
                }
                
                ApiResponse.success(conflictList, "模块冲突列表获取成功，共 ${conflictList.size} 个冲突")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取模块冲突列表失败", e)
                ApiResponse.error("获取模块冲突列表失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取文件差异信息
     * 
     * @param resourceId 资源ID
     * @return API响应，包含文件差异信息
     */
    suspend fun getFileDiff(resourceId: String): ApiResponse<FileDiffResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val diffInfo = ResourceConflictManager.getFileDiff(resourceId)
                
                if (diffInfo == null) {
                    return@withContext ApiResponse.error<FileDiffResponse>("未找到资源冲突: $resourceId")
                }
                
                val response = FileDiffResponse(
                    resourceId = diffInfo.resourceId,
                    currentContent = diffInfo.currentContent,
                    legacyContent = diffInfo.legacyContent,
                    currentFile = diffInfo.currentFile.toString(),
                    legacyFile = diffInfo.legacyFile.toString(),
                    diffLines = calculateDiffLines(diffInfo.currentContent, diffInfo.legacyContent)
                )
                
                ApiResponse.success(response, "文件差异获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取文件差异失败", e)
                ApiResponse.error("获取文件差异失败: ${e.message}")
            }
        }
    }
    
    /**
     * 解决冲突 - 使用当前版本
     * 
     * @param resourceId 资源ID
     * @return API响应，包含操作结果
     */
    suspend fun resolveConflictUseCurrent(resourceId: String): ApiResponse<ConflictResolutionResult> {
        return withContext(Dispatchers.IO) {
            try {
                ResourceConflictManager.resolveConflictUseCurrent(resourceId)
                
                val result = ConflictResolutionResult(
                    resourceId = resourceId,
                    resolution = "USE_CURRENT",
                    success = true,
                    message = "已使用当前版本解决冲突"
                )
                
                ApiResponse.success(result, "冲突解决成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("解决冲突失败", e)
                ApiResponse.error("解决冲突失败: ${e.message}")
            }
        }
    }
    
    /**
     * 解决冲突 - 恢复Legacy版本
     * 
     * @param resourceId 资源ID
     * @return API响应，包含操作结果
     */
    suspend fun resolveConflictUseLegacy(resourceId: String): ApiResponse<ConflictResolutionResult> {
        return withContext(Dispatchers.IO) {
            try {
                ResourceConflictManager.resolveConflictUseLegacy(resourceId)
                
                val result = ConflictResolutionResult(
                    resourceId = resourceId,
                    resolution = "USE_LEGACY",
                    success = true,
                    message = "已恢复Legacy版本解决冲突"
                )
                
                ApiResponse.success(result, "冲突解决成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("解决冲突失败", e)
                ApiResponse.error("解决冲突失败: ${e.message}")
            }
        }
    }
    
    /**
     * 批量解决冲突
     * 
     * @param operations 批量操作列表
     * @return API响应，包含批量操作结果
     */
    suspend fun batchResolveConflicts(
        operations: List<ConflictResolutionOperation>
    ): ApiResponse<BatchConflictResolutionResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<ConflictResolutionResult>()
                var successCount = 0
                var failureCount = 0
                
                operations.forEach { operation ->
                    try {
                        when (operation.resolution) {
                            "USE_CURRENT" -> {
                                ResourceConflictManager.resolveConflictUseCurrent(operation.resourceId)
                                successCount++
                                results.add(
                                    ConflictResolutionResult(
                                        resourceId = operation.resourceId,
                                        resolution = operation.resolution,
                                        success = true,
                                        message = "成功使用当前版本"
                                    )
                                )
                            }
                            "USE_LEGACY" -> {
                                ResourceConflictManager.resolveConflictUseLegacy(operation.resourceId)
                                successCount++
                                results.add(
                                    ConflictResolutionResult(
                                        resourceId = operation.resourceId,
                                        resolution = operation.resolution,
                                        success = true,
                                        message = "成功恢复Legacy版本"
                                    )
                                )
                            }
                            else -> {
                                failureCount++
                                results.add(
                                    ConflictResolutionResult(
                                        resourceId = operation.resourceId,
                                        resolution = operation.resolution,
                                        success = false,
                                        message = "未知的解决方案: ${operation.resolution}"
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        failureCount++
                        results.add(
                            ConflictResolutionResult(
                                resourceId = operation.resourceId,
                                resolution = operation.resolution,
                                success = false,
                                message = "异常: ${e.message}"
                            )
                        )
                    }
                }
                
                val batchResult = BatchConflictResolutionResult(
                    totalOperations = operations.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    results = results
                )
                
                ApiResponse.success(batchResult, "批量解决冲突完成: 成功 $successCount 个，失败 $failureCount 个")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("批量解决冲突失败", e)
                ApiResponse.error("批量解决冲突失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取冲突统计信息
     * 
     * @return API响应，包含冲突统计信息
     */
    suspend fun getConflictStats(): ApiResponse<ConflictStats> {
        return withContext(Dispatchers.IO) {
            try {
                val allConflicts = ResourceConflictManager.getAllConflicts()
                
                // 按模块统计
                val moduleStats = allConflicts.values.groupBy { 
                    "${it.resourceNode.modId}:${it.resourceNode.moduleName}" 
                }.mapValues { it.value.size }
                
                // 按资源类型统计
                val typeStats = allConflicts.values.groupBy { 
                    inferResourceType(it.resourceNode.id.path) 
                }.mapValues { it.value.size }
                
                // 按命名空间统计
                val namespaceStats = allConflicts.values.groupBy { 
                    it.resourceNode.namespace 
                }.mapValues { it.value.size }
                
                val stats = ConflictStats(
                    totalConflicts = allConflicts.size,
                    moduleStats = moduleStats,
                    resourceTypeStats = typeStats,
                    namespaceStats = namespaceStats
                )
                
                ApiResponse.success(stats, "冲突统计信息获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取冲突统计信息失败", e)
                ApiResponse.error("获取冲突统计信息失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清理Legacy文件夹
     * 
     * @return API响应，包含操作结果
     */
    suspend fun cleanupLegacyFolder(): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ResourceConflictManager.cleanupLegacyFolder()
                ApiResponse.success(true, "Legacy文件夹清理成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("清理Legacy文件夹失败", e)
                ApiResponse.error("清理Legacy文件夹失败: ${e.message}")
            }
        }
    }
    
    /**
     * 计算文件差异行
     */
    private fun calculateDiffLines(currentContent: String, legacyContent: String): List<DiffLine> {
        val currentLines = currentContent.lines()
        val legacyLines = legacyContent.lines()
        val diffLines = mutableListOf<DiffLine>()
        
        // 简单的行对比算法
        val maxLines = maxOf(currentLines.size, legacyLines.size)
        
        for (i in 0 until maxLines) {
            val currentLine = if (i < currentLines.size) currentLines[i] else null
            val legacyLine = if (i < legacyLines.size) legacyLines[i] else null
            
            when {
                currentLine == null -> {
                    // 只在Legacy中存在
                    diffLines.add(DiffLine(i + 1, null, legacyLine, "REMOVED"))
                }
                legacyLine == null -> {
                    // 只在当前版本中存在
                    diffLines.add(DiffLine(i + 1, currentLine, null, "ADDED"))
                }
                currentLine == legacyLine -> {
                    // 相同行
                    diffLines.add(DiffLine(i + 1, currentLine, legacyLine, "UNCHANGED"))
                }
                else -> {
                    // 修改的行
                    diffLines.add(DiffLine(i + 1, currentLine, legacyLine, "MODIFIED"))
                }
            }
        }
        
        return diffLines
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
 * 冲突信息
 */
data class ConflictInfo(
    val resourceId: String,
    val resourceName: String,
    val namespace: String,
    val modId: String,
    val moduleName: String,
    val resourceType: String,
    val currentFile: String,
    val legacyFile: String,
    val detectedTime: Long
)

/**
 * 文件差异响应
 */
data class FileDiffResponse(
    val resourceId: String,
    val currentContent: String,
    val legacyContent: String,
    val currentFile: String,
    val legacyFile: String,
    val diffLines: List<DiffLine>
)

/**
 * 差异行
 */
data class DiffLine(
    val lineNumber: Int,
    val currentLine: String?,
    val legacyLine: String?,
    val changeType: String // UNCHANGED, ADDED, REMOVED, MODIFIED
)

/**
 * 冲突解决操作
 */
data class ConflictResolutionOperation(
    val resourceId: String,
    val resolution: String // USE_CURRENT, USE_LEGACY
)

/**
 * 冲突解决结果
 */
data class ConflictResolutionResult(
    val resourceId: String,
    val resolution: String,
    val success: Boolean,
    val message: String
)

/**
 * 批量冲突解决结果
 */
data class BatchConflictResolutionResult(
    val totalOperations: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<ConflictResolutionResult>
)

/**
 * 冲突统计信息
 */
data class ConflictStats(
    val totalConflicts: Int,
    val moduleStats: Map<String, Int>,
    val resourceTypeStats: Map<String, Int>,
    val namespaceStats: Map<String, Int>
)
